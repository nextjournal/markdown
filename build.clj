(ns build
  (:require [clojure.tools.build.api :as b]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [deps-deploy.deps-deploy :as dd]))

(def lib 'io.github.nextjournal/markdown)
(def previous-repo-offset 69) ;; used to live in nextjournal/viewers
(def major 0)
(def minor 3)

(defn current-git-commit-sha [] (str/trim (:out (shell/sh "git" "rev-parse" "HEAD"))))
(def scm {:url (format "https://github.com/nextjournal/markdown/tree/%s" (current-git-commit-sha))})
(def version (format "%s.%s.%s" major minor (+ previous-repo-offset (Integer/parseInt (b/git-count-revs nil)))))
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def jar-file (format "target/%s-%s.jar" (name lib) version))

(defn clean [_]
  (b/delete {:path "target"}))

(defn jar [_]
  (b/write-pom {:basis basis
                :class-dir class-dir
                :lib lib
                :scm scm
                :src-dirs ["src"]
                :version version})
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file jar-file}))

(defn deploy [opts]
  (println "Deploying version" jar-file "to Clojars.")
  (jar {})
  (dd/deploy (merge {:installer :remote
                     :artifact jar-file
                     :pom-file (b/pom-path {:lib lib :class-dir class-dir})}
                    opts)))

(comment
  (jar 1)
  (clean 1)
  (b/git-count-revs nil)
  (current-git-commit-sha))
