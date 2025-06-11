(ns nextjournal.markdown.multi-threading-test
  (:require [babashka.fs :as fs]
            [clojure.java.io :as io]
            [clojure.test :as t :refer [deftest is]]
            [nextjournal.markdown :as md]))

(def notebook-file
  ;; we do this so test can be executed from arbitrary directory (i.e. in babashka's test suite)
  (-> (or (io/resource *file*) *file*)
      fs/parent
      fs/parent
      fs/parent
      fs/parent
      (fs/file "notebooks" "reference.md")))

(deftest multithreading
  (let [!exs (atom [])
        proc (fn []
               (try (md/parse (slurp notebook-file))
                    (catch IllegalStateException e
                      (swap! !exs conj e))))
        t1 (new Thread proc)
        t2 (new Thread proc)]

    (.start t1) (.start t2)
    (.join t1) (.join t2)
    (is (zero? (count @!exs)))))
