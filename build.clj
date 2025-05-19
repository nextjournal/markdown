(ns build
  (:require [clojure.tools.build.api :as b]
            [deps-deploy.deps-deploy :as dd]))

(def lib 'io.github.nextjournal/markdown)

(defn scm [version]
  {:url "https://github.com/nextjournal/markdown"
   :tag (str "v" version)
   :connection "scm:git:git://github.com/nextjournal/markdown.git"
   :developerConnection "scm:git:ssh://git@github.com/nextjournal/markdown.git"})

(def class-dir "target/classes")

(def basis (b/create-basis {:project "deps.edn"}))

(defn jar-file [version] (format "target/%s-%s.jar" (name lib) version))

(defn clean [_] (b/delete {:path "target"}))

(defn jar [{:keys [version]}]
  (b/delete {:path "target"})
  (println "Producing jar: " (jar-file version))
  (b/write-pom {:basis basis
                :class-dir class-dir
                :lib lib
                :scm (scm version)
                :src-dirs ["src"]
                :version version
                :pom-data
                [[:licenses
                  [:license
                   [:name "ISC License"]
                   [:url "https://opensource.org/license/isc-license-txt"]]]]})
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir
               :replace {}})
  (b/jar {:class-dir class-dir
          :jar-file (jar-file version)}))

(defn install [{:keys [version] :as opts}]
  (jar opts)
  (b/install {:basis basis
              :lib lib
              :version (:version opts)
              :jar-file (jar-file version)
              :class-dir class-dir}))

(defn deploy [{:keys [version] :as opts}]
  (println "Deploying version" (jar-file version) "to Clojars.")
  (jar opts)
  (dd/deploy {:installer :remote
              :artifact (jar-file version)
              :pom-file (b/pom-path {:lib lib :class-dir class-dir})}))
