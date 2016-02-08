(ns ostronom.uploader
  (:require-macros
   [cljs.core.async.macros :refer [go-loop go]])
  (:import goog.net.XhrIo)
  (:require
   [goog.events :as ge]
   [goog.net.EventType :as ET]
   [cljs.core.async :refer [<! >! chan close! put! timeout mix admix]]))

(defprotocol IUploader
  (upload
    [u target file form-data]
    [u target file form-data file-name]))

(def html5supported?
  (not (or (undefined? js/File)
           (undefined? js/Blob)
           (undefined? js/FileList)
           (not (or js/Blob.prototype.webkitSlice
                    js/Blob.prototype.mozSlice
                    js/Blob.prototype.slice
                    false)))))
(def slicer
  (if html5supported?
    (cond
      js/Blob.prototype.slice       #(.slice %1 %2 %3)
      js/Blob.prototype.moSlice     #(.mozSlice %1 %2 %3)
      js/Blob.prototype.webkitSlice #(.webkitSlice %1 %2 %3))))

(defn inject-form-data [form data]
  (doseq [[k v] data] (.append form k v))
  form)

(defn notify-completion [ch]
  (put! ch [:complete])
  (close! ch))

(defn notify-progress [ch loaded total]
  (put! ch [:progress {:total total :loaded loaded}]))

(defn notify-error [ch]
  (put! ch [:error])
  (close! ch))

(deftype ClassicUploader [file-field]
  IUploader
  (upload [u target file form-data]
    (upload u target file form-data (.-name file)))
  (upload [u target file form-data file-name]
    (let [res    (chan)
          params (doto (js/FormData.)
                   (.append file-field file file-name)
                   (inject-form-data form-data))]
      (doto (goog.net.XhrIo.)
        (.setProgressEventsEnabled true)
        (ge/listen ET/UPLOAD_PROGRESS #(notify-progress res (.-loaded %) (.-total %)))
        (ge/listen ET/ERROR #(notify-error res))
        (ge/listen ET/COMPLETE #(notify-completion res))
        (.send target "POST" params #js {}))
      res)))

(deftype ChunkedUploader
  [file-field total-field offset-field chunk-size timeout-fn max-retries]
  IUploader
  (upload [this target file form-data]
    (upload this target file form-data (.-name file)))
  (upload [this target file form-data fname]
    (let [res        (chan)
          upchan     (chan)
          upmix      (mix upchan)
          total      (.-size file)
          type       (.-type file)
          data       (assoc form-data total-field total)
          uploader   (ClassicUploader. file-field)
          send-chunk (fn [offset]
                       (if (>= offset total)
                         (notify-completion res)
                         (let [end (min (+ offset chunk-size) total)]
                           (admix upmix
                             (upload
                               uploader
                               target
                               (slicer file offset end type)
                               (assoc data offset-field offset)
                               fname)))))]
      (go-loop [offset 0 retry 0]
        (let [[cmd props] (<! upchan)]
          (condp = cmd
            :complete
            (do
              (send-chunk offset)
              (recur (+ chunk-size offset) retry))

            :error
            (if (or (nil? max-retries) (<= retry max-retries))
             (do
               (<! (timeout (timeout-fn retry)))
               (send-chunk offset)
               (recur offset (inc retry)))
             (notify-error res))

            :progress
            (let [{:keys [total loaded]} props]
              (notify-progress res (+ offset loaded) total)
              (recur offset retry))

            (recur offset retry))))
      (let [dumb (chan)]
        (admix upmix dumb)
        (notify-completion dumb))
      res)))

(def defaults {:file-field "file"
               :chunk-size (* 512 1024)
               :timeout-fn (constantly 3000)
               :total-field "total"
               :offset-field "offset"
               :max-retries nil})

(defn get-uploader
  ([] (get-uploader defaults))
  ([opts]
    (let [{:keys [file-field total-field offset-field chunk-size timeout-fn max-retries]}
          (merge defaults opts)]
      (if html5supported?
        (ChunkedUploader. file-field total-field offset-field chunk-size timeout-fn max-retries)
        (ClassicUploader. file-field)))))
