(ns tasks
  (:require
   [babashka.fs :as fs]
   [babashka.http-client :as http]
   [babashka.process :as p]))


(def inspector-zip-url "https://github.com/cjohansen/gadget-inspector/archive/refs/heads/master.zip")

(defn ^:export build-inspector-extension! []
  (println "Building Gadget Inspector")
  (let [build-dir (fs/create-dirs (fs/path "gadget-inspector"))
        zip-file (fs/path build-dir "gadget-inspector.zip")
        _ (println "Downloading inspector")
        zip-data (:body (http/get inspector-zip-url {:as :bytes}))
        gadget-inspector-dir (fs/path build-dir "gadget-inspector-master")
        _ (println "Saving zip to:" zip-file)
        _ (fs/write-bytes zip-file zip-data)
        _ (println "Unzipping  to:" gadget-inspector-dir)
        _ (fs/unzip zip-file build-dir {:replace-existing true})
        _ (println "Buildling Chrome Extension")
        result (p/shell {:dir gadget-inspector-dir} "make" "extension")]
    (if (zero? (:exit result))
      (do
        (println "Extension built to:" (-> (fs/path gadget-inspector-dir "extension")
                                           (fs/absolutize)
                                           str))
        (println "Open chrome://extensions/ in your Chromium browser"))
      (println "Something went wrong building the extension:" result))))

(comment
  (build-inspector-extension!)
  :rcf)

