(ns test-runner
  (:require [clojure.test]
            [nextjournal.markdown-test]))

(defn run [_]
  (let [{:keys [fail error]} (clojure.test/run-all-tests #"nextjournal\.markdown.*-test")]
    (when (< 0 (+ fail error))
      (System/exit 1))))
