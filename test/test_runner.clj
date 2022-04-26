(ns test-runner
  (:require [clojure.test]
            [nextjournal.markdown-test]))

(defn run
  "This is meant to be run via -X alias as `clj -X:test/markdown`"
  [_]
  (let [{:keys [fail error]} (clojure.test/run-tests 'nextjournal.markdown-test)]
    (when (< 0 (+ fail error))
      (System/exit 1))))
