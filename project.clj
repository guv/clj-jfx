(defproject clj-jfx "0.1.24"

  :license {:name "Eclipse Public License v2.0"
            :url "http://www.eclipse.org/legal/epl-v20.html"}

  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.slf4j/slf4j-log4j12 "1.7.18"]
                 [org.openjfx/javafx-controls "11"]
                 [org.openjfx/javafx-fxml "11"]
                 [org.openjfx/javafx-swing "11"]
                 ; fontawesomefx 9
                 [de.jensd/fontawesomefx-emojione "3.1.1-9.1.2"]
                 [de.jensd/fontawesomefx-fontawesome "4.7.0-9.1.2"]
                 [de.jensd/fontawesomefx-icons525 "4.2.0-9.1.2"]
                 [de.jensd/fontawesomefx-materialdesignfont "2.0.26-9.1.2"]
                 [de.jensd/fontawesomefx-materialicons "2.2.0-9.1.2"]
                 [de.jensd/fontawesomefx-octicons "4.3.0-9.1.2"]
                 [de.jensd/fontawesomefx-weathericons "2.0.10-9.1.2"]]

  :source-paths ["src/clojure"]
  :java-source-paths ["src/java"]

  :java-cmd "/usr/bin/java11"

  :profiles {:dev {:jvm-opts ^:replace ["-XX:-OmitStackTraceInFastThrow" "-XX:+UseG1GC"]
                   :dependencies [[org.clojure/test.check "0.10.0-alpha3"]]}}

  :repositories ^:replace [["fontawesomefx" {:url "https://dl.bintray.com/jerady/maven"}]]

  :repl-options {:init (do
                         (require 'clj-jfx.init)
                         (clj-jfx.init/init)
                         (require 'clj-jfx.core)
                         (clj-jfx.core/implicit-exit! false))})
