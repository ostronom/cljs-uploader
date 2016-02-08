# Chunked uploader

## Description

ClojureScript micro-library that helps you make the process of uploading large
files easier for users. It splits file in chunks and uploads them sequentially
with retries (and timeouts between them) on failure. Falls back to normal upload
if blob slicing isn't supported by user's browser.

## Usage

[![Clojars Project](https://img.shields.io/clojars/v/org.clojars.ostronom/cljs-uploader.svg)](https://clojars.org/org.clojars.ostronom/cljs-uploader)


```
(ns myns.files
  (:require-macros [cljs.core.async.macros :refer [go-loop]])
  (:require [ostronom.uploader :as up]
            [cljs.core.async :refer [<!]]))

(defn i-want-to-upload [file]
  (let [uploader (up/get-uploader)
        ch       (up/upload uploader "/files" file {"form-field" "value"})]
    (go-loop []
      (when-let [[evt data] (<! ch)]
        (cond evt
          :progress
          (js/console.log "UPLOADED " (:loaded data) "OUT OF " (:total evt))

          :complete
          (js/console.log "DONE")

          :error
          (js/console.log ":(")))))))
```

`get-uploader` returns `IUploader` instance with `upload` method with two arities: `[this target file form-data]` and `[u target file form-data file-name]`

where:
- `target` is URL where file should be submitted
- `file` is FormData file instance
- `form-data` hash-map of additional form data to be sent with a file
- `file-name` overrides file name of `file`

You can provide configuration options to `get-uploader` like this `(get-uploader opts)`, where `opts` is a hash with some options:
- `:file-field` form field which should contain file (default: `"file"`)
- `:chunk-size` size of each chunk in bytes (default: `524288`)
- `:timeout-fn` timeout function which should take number of upload attempt and return number of ms to sleep before another attempt (defaults to constantly 3000 ms)
- `:total-field` form field which should contain total file size of uploaded file (default: `"total"`)
- `:offset-field` form field which should contain offset of current chunk from the beginning (default: `"offset"`)
- `:max-retries` number of retries on chunk upload error. `nil` value means infinite retries (default: `nil`)

`upload` method returns `core.async` channel with following messages:
- `[:complete]` -- when upload is complete
- `[:error]` -- when there was an error after all of the retries
- `[:progress {:total N :loaded M}]` -- informs you that M out of N bytes are uploaded

## Server-side

Obviously, your server should support "gluing" of those chunks together.
