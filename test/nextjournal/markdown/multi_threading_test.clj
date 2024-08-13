(ns nextjournal.markdown.multi-threading-test
  (:require [clojure.test :as t :refer [deftest testing is]]
            [nextjournal.markdown :as md]))

(deftest multithreading
  (let [!exs (atom [])
        proc (fn []
               (try (md/parse (slurp "notebooks/reference.md"))
                    (catch IllegalStateException e
                      (swap! !exs conj e))))
        t1 (new Thread proc)
        t2 (new Thread proc)]

    (.start t1) (.start t2)
    (.join t1) (.join t2)
    (is (zero? (count @!exs)))))
