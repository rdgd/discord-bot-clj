(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib 'io.rdgd/discord-bot)
(def version "0.3.1")
(def class-dir "target/classes")
(def jar-file "target/discord-bot.jar")
(def basis (delay (b/create-basis {:project "deps.edn"})))

(defn clean [_]
  (b/delete {:path "target"}))

(defn jar [_]
  (clean nil)
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis @basis
                :src-dirs ["src"]
                :pom-data [[:licenses
                            [:license
                             [:name "MIT"]
                             [:url "https://opensource.org/licenses/MIT"]]]]})
  (b/copy-dir {:src-dirs ["src"]
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file jar-file}))
