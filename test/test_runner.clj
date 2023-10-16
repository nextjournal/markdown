(ns test-runner
  (:require [clojure.test]
            [nextjournal.markdown-test]
            [nextjournal.markdown.multi-threading-test]))

(defn run [_]
  (let [{:keys [fail error]} (clojure.test/run-all-tests #"nextjournal\.markdown.*-test")]
    (when (< 0 (+ fail error))
      (System/exit 1))))

#_(clojure.test/run-all-tests #"nextjournal\.markdown.*-test")
