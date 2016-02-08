(defproject com.ostronom/cljs-chunk-uploader "0.0.1"
  :dependencies [[org.clojure/clojure "1.8.0" :scope "provided"]
                 [org.clojure/clojurescript "1.7.170" :scope "provided"]
                 [org.clojure/core.async "0.2.374"]]
  :source-paths ["src/main"]
  :clean-targets ^{:protect false} ["resources/out"]
  :profiles {:dev {:plugins [[lein-ancient "0.6.8"]
                             [lein-cljfmt "0.3.0"]
                             [lein-cljsbuild "1.1.2"]]}}
  :cljsbuild
  {:builds
    {:main {:source-paths ["src/main" "src/dev"]
            :compiler {:output-to "resources/out/main.js"
                       :main uploader.dev
                       :optimizations :none}}}})
