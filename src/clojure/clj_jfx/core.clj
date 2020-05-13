; Copyright (c) Gunnar Völkel. All rights reserved.
; The use and distribution terms for this software are covered by the
; Eclipse Public License 2.0 (http://www.eclipse.org/legal/epl-v20.html)
; which can be found in the file LICENSE at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license.
; You must not remove this notice, or any other, from this software.

(ns clj-jfx.core
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [clj-jfx.control :as ctrl]
    [clj-jfx.util :as u]
    [clj-jfx.traversal :as t]
    [clj-jfx.colors :as col]
    [clojure.set :as set])
  (:import
    javax.imageio.ImageIO
    javafx.geometry.Rectangle2D
    javafx.embed.swing.SwingFXUtils
    javafx.scene.paint.Color
    (javafx.application Platform Application)
    (javafx.scene Scene Node SnapshotParameters Parent)
    (javafx.scene.control Alert Alert$AlertType MenuItem Label Labeled Control Tooltip ContextMenu ComboBox TableColumn TableColumn$CellDataFeatures TextInputDialog TableView ButtonType CheckBox ColorPicker TitledPane TextField TextFormatter TextFormatter$Change ToolBar ListCell ListView TextInputControl ToggleGroup Toggle SeparatorMenuItem TabPane Button Hyperlink Spinner SpinnerValueFactory$IntegerSpinnerValueFactory SpinnerValueFactory$DoubleSpinnerValueFactory SpinnerValueFactory MultipleSelectionModel)
    (javafx.scene.layout Region BackgroundFill CornerRadii Background BorderStroke Border BorderStrokeStyle BorderWidths GridPane Pane VBox ColumnConstraints RowConstraints Priority HBox)
    (javafx.stage FileChooser FileChooser$ExtensionFilter Stage Modality Window WindowEvent StageStyle)
    (javafx.event EventHandler EventType ActionEvent Event)
    (javafx.util Callback StringConverter)
    (javafx.geometry Insets Pos)
    (javafx.scene.shape Shape Circle)
    (javafx.scene.text TextAlignment Font FontWeight FontPosture TextFlow Text FontSmoothingType)
    (javafx.fxml FXMLLoader)
    (java.util Collection List Locale)
    (java.io File)
    (javafx.scene.image Image)
    (java.awt.image BufferedImage)
    (javafx.beans.value ObservableValue ChangeListener WritableValue)
    (javafx.collections ObservableList FXCollections)
    (javafx.beans InvalidationListener Observable)
    (javafx.beans.property Property SimpleObjectProperty)
    (java.lang.ref WeakReference)
    (clj_jfx.table RadioButtonTableCell CheckBoxTableCell ColorPickerTableCell ComboBoxSelectionTableCell)
    (javafx.beans.binding Bindings)
    (com.sun.javafx.binding ExpressionHelper)
    (java.util.function UnaryOperator Predicate)
    (java.text ParsePosition NumberFormat DecimalFormat)
    (javafx.scene.input DragEvent MouseEvent MouseDragEvent ScrollEvent MouseButton KeyEvent ClipboardContent Clipboard KeyCode)
    (javafx.css Styleable)
    (clj_jfx.list GenericListCell)
    (javafx.scene.canvas Canvas)
    (javafx.collections.transformation FilteredList SortedList)
    (com.sun.javafx.stage StageHelper)
    (javafx.scene.paint Paint)))



(defn execute-safe
  [context, f]
  (try
    (f)
    (catch Throwable t
      (u/log-exception (format "Exception in %s with function: %s" context (class f)) t)
      t)))


(defn run-later*
  [f]
  (let [result (promise)]
    (Platform/runLater (fn [] (deliver result (execute-safe "run-later*" f))))
    result))


(defmacro run-later
  [& body]
  `(run-later*
     (^:once fn* [] ~@body)))


(defn run-now*
  [f]
  (let [result (if (Platform/isFxApplicationThread)
                 (execute-safe "run-now*" f)
                 (deref (run-later* f)))]
    (if (instance? Throwable result)
      (throw result)
      result)))


(defmacro run-now
  [& body]
  `(run-now*
     (^:once fn* [] ~@body)))


(defmacro callback
  [[param] & body]
  `(reify Callback
     (call [_, ~param]
       ~@body)))


(defn ->callback
  [f]
  (if (instance? Callback f)
    f
    (reify Callback
      (call [this, param]
        (f param)))))


(defn node-id
  [node]
  (when (instance? Styleable node)
    (.getId ^Styleable node)))


(defn find-node
  [parent-node, id]
  (t/traverse-pre-order
    ; init: no result, combine: take new value
    (fn ([] nil) ([result, value] value)),
    (fn [node]
      ; when not is found, stop and return it
      (when (= (node-id node) id)
        (t/traversed node)))
    parent-node))



(defn load-control
  "Loads a control from the given FXML resource file."
  [fxml-file]
  (if-let [res (io/resource fxml-file)]
    (let [loader (doto (FXMLLoader.)
                   (.setLocation res))]
      (.load loader))
    (u/illegal-argument "Resource \"%s\" not found!" fxml-file)))


(defn visible!
  [^Node node, visible?]
  (doto node
    (.setVisible visible?)))


(defn enabled!
  [^Node node, enabled?]
  (doto node
    (.setDisable (not enabled?))))


(defn disable!
  [^Node node, disable?]
  (doto node
    (.setDisable disable?)))


(defn editable!
  [^TextInputControl control, editable?]
  (doto control
    (.setEditable editable?)))


(defn parent
  [^Node node]
  (.getParent node))


(defn conver-event-type
  ^EventType [event-type]
  (case event-type
    :action ActionEvent/ACTION))


(defn event-handler
  "Create an event handler from the given handler function."
  (^EventHandler [handler-fn]
   (event-handler handler-fn, nil))
  (^EventHandler [handler-fn, args]
   (when handler-fn
     (cond
       (nil? handler-fn) (u/illegal-argument "encountered nil instead of a handler function or an EventHandler instance")
       (instance? EventHandler handler-fn) (if (seq args)
                                             (u/illegal-argument "when passing an EventHandler instance, additional arguments are not supported")
                                             handler-fn)
       (fn? handler-fn) (reify EventHandler (handle [this, event] (u/safe (apply handler-fn event, args))))
       :else (u/illegal-argument "event handler or function for event handling expected but got %s" (type handler-fn))))))


(defn add-single-event-handler!
  [^Node node, event-type, handler]
  (.addEventHandler node, (conver-event-type event-type), (event-handler handler))
  node)


(defn set-menu-item-action!
  ([^Scene scene, menu-item-id, handler-fn]
   (set-menu-item-action! (find-node scene, menu-item-id), handler-fn))
  ([^MenuItem menu-item, handler-fn]
   (doto menu-item
     (.setOnAction (event-handler handler-fn)))))


(defn menu-item-actions!
  [^Scene scene, menu-id-path->handler-fn-map]
  (reduce-kv
    (fn [scene, menu-item-id, handler-fn]
      (doto scene
        (set-menu-item-action! menu-item-id, handler-fn)))
    scene
    menu-id-path->handler-fn-map))


(def event-types
  {:action ActionEvent/ACTION
   :window-showing WindowEvent/WINDOW_SHOWING
   :window-shown WindowEvent/WINDOW_SHOWN
   :window-hiding WindowEvent/WINDOW_HIDING
   :window-hidden WindowEvent/WINDOW_HIDDEN
   :window-close-request WindowEvent/WINDOW_CLOSE_REQUEST
   :drag-detected MouseEvent/DRAG_DETECTED
   :drag-over DragEvent/DRAG_OVER
   :drag-entered DragEvent/DRAG_ENTERED,
   :drag-exited DragEvent/DRAG_EXITED,
   :drag-dropped DragEvent/DRAG_DROPPED,
   :drag-done DragEvent/DRAG_DONE,
   :mouse-drag-over MouseDragEvent/MOUSE_DRAG_OVER,
   :mouse-drag-entered MouseDragEvent/MOUSE_DRAG_ENTERED,
   :mouse-drag-exited MouseDragEvent/MOUSE_DRAG_EXITED,
   :mouse-dragged MouseDragEvent/MOUSE_DRAGGED,
   :mouse-drag-released MouseDragEvent/MOUSE_DRAG_RELEASED,
   :mouse-entered MouseEvent/MOUSE_ENTERED,
   :mouse-exited MouseEvent/MOUSE_EXITED,
   :mouse-moved MouseEvent/MOUSE_MOVED,
   :mouse-pressed MouseEvent/MOUSE_PRESSED,
   :mouse-released MouseEvent/MOUSE_RELEASED,
   :mouse-clicked MouseEvent/MOUSE_CLICKED,
   :key-pressed KeyEvent/KEY_PRESSED,
   :key-typed KeyEvent/KEY_TYPED,
   :key-released KeyEvent/KEY_RELEASED,
   :scroll ScrollEvent/SCROLL
   :scroll-started ScrollEvent/SCROLL_STARTED
   :scroll-finished ScrollEvent/SCROLL_FINISHED})


(defn to-event-type
  ^EventType [event]
  (get event-types event))


(defn supported-event-types
  []
  (into (sorted-set) (keys event-types)))


(defn drag-and-drop-event-handler-map
  [handler-fn, & args]
  (zipmap
    [:drag-detected, :drag-over, :drag-entered, :drag-exited, :drag-dropped, :drag-done]
    (repeat (event-handler handler-fn, args))))


(defn filter-event!
  [^Node node, event-type, handler-fn]
  (let [handler (event-handler handler-fn)]
    (if-let [event-type (to-event-type event-type)]
      (.addEventFilter node, event-type, handler)
      (u/illegal-argument "unsupported event type %s - the following types are supported: %s" event-type, (str/join ", " (supported-event-types))))
    node))


(defn handle-event!
  [node-or-stage, event-type, handler-fn, & args]
  (let [handler (event-handler handler-fn, args)]
    (if-let [event-type (to-event-type event-type)]
      (cond
        (instance? Node node-or-stage) (.addEventHandler ^Node node-or-stage, event-type, handler)
        (instance? Stage node-or-stage) (.addEventHandler ^Stage node-or-stage, event-type, handler)
        :else (u/illegal-argument "Event source must be either a Node or a Stage! Not %s" (type node-or-stage)))
      (u/illegal-argument "unsupported event type %s - the following types are supported: %s" event-type, (str/join ", " (supported-event-types))))
    node-or-stage))


(defn consume
  [^Event e]
  (doto e
    (.consume)))


(defn event-handling!
  [parent, id-event-handler-tuples]
  (reduce
    (fn [parent, [id, event, handler]]
      (doto parent
        (-> (find-node id) (handle-event! event, handler))))
    (if (instance? Stage parent)
      (.getScene ^Stage parent)
      parent)
    id-event-handler-tuples))


(defn button-actions!
  "Set button actions of the buttons corresponding to the given ids."
  [^Scene scene, button-id->handler-fn-map]
  (reduce-kv
    (fn [scene, button-id, handler-fn]
      (let [button (find-node scene, button-id)]
        (handle-event! button :action handler-fn)
        scene))
    scene
    button-id->handler-fn-map))


(defn size-to-scene!
  [^Stage stage]
  (doto stage
    (.sizeToScene)))


(defn min-width!
  [^Region node, ^double value]
  (doto node (.setMinWidth value)))

(defn pref-width!
  [^Region node, ^double value]
  (doto node (.setPrefWidth value)))

(defn max-width!
  [^Region node, ^double value]
  (doto node (.setMaxWidth value)))


(defn min-height!
  [^Region node, ^double value]
  (doto node (.setMinHeight value)))

(defn pref-height!
  [^Region node, ^double value]
  (doto node (.setPrefHeight value)))

(defn max-height!
  [^Region node, ^double value]
  (doto node (.setMaxHeight value)))

(defn min-size!
  ([region, [width, height]]
   (min-size! region, width, height))
  ([^Region region, ^double width, ^double height]
   (doto region
     (.setMinSize width, height))))


(defn preferred-size!
  ([region, [width, height]]
   (preferred-size! region, width, height))
  ([^Region region, ^double width, ^double height]
   (doto region
     (.setPrefSize width, height))))

(defn max-size!
  ([region, [width, height]]
   (max-size! region, width, height))
  ([^Region region, ^double width, ^double height]
   (doto region
     (.setMaxSize width, height))))

(defn use-computed-size!
  [^Region region]
  (doto region
    (preferred-size! Region/USE_COMPUTED_SIZE, Region/USE_COMPUTED_SIZE)
    (max-size! Region/USE_COMPUTED_SIZE, Region/USE_COMPUTED_SIZE)))

(defn use-computed-size-and-grow!
  [^Region region]
  (doto region
    (min-size! Region/USE_PREF_SIZE, Region/USE_PREF_SIZE)
    (preferred-size! Region/USE_COMPUTED_SIZE, Region/USE_COMPUTED_SIZE)
    (max-size! Double/MAX_VALUE, Double/MAX_VALUE)))


(defn show-alert
  [alert-type, title, header, content]
  (run-now
    (let [alert (doto (Alert. ({:information Alert$AlertType/INFORMATION,
                                :error Alert$AlertType/ERROR,
                                :warning Alert$AlertType/WARNING,
                                :confirmation Alert$AlertType/CONFIRMATION} alert-type))
                  (.setTitle title)
                  (.setHeaderText header)
                  (.setContentText content))
          dialog-pane (.getDialogPane alert)]
      (use-computed-size-and-grow! dialog-pane)
      (doseq [node (.getChildren dialog-pane)]
        (use-computed-size-and-grow! node))
      (.showAndWait alert))))


(defn show-information
  [title, header, content]
  (show-alert :information, title, header, content))


(defn show-error
  [title, header, content]
  (show-alert :error, title, header, content))


(defn ask-confirmation
  [title, header, content]
  (run-now
    (let [alert (doto (Alert. Alert$AlertType/CONFIRMATION, content, (into-array ButtonType [ButtonType/YES, ButtonType/NO]))
                  (.setTitle title)
                  (.setHeaderText header)
                  (-> .getDialogPane
                    use-computed-size-and-grow!)),
          result (.showAndWait alert)]
      (and (.isPresent result) (= (.get result) ButtonType/YES)))))


(defn extension-filter
  [name, extension-list]
  (FileChooser$ExtensionFilter. ^String name, ^java.util.List extension-list))


(defn file-extension-set
  [^FileChooser chooser]
  (->> chooser
    .getExtensionFilters
    (mapcat #(.getExtensions ^FileChooser$ExtensionFilter %))
    (map u/file-extension)
    set))


(defn choose-file
  "Show a file open dialog with the given title and the specified file extensions.
  Example: (choose-file \"Import CSV\", {\"CSV Files\" [\"*.csv\", \"*.tsv\"]})"
  (^File [stage, title, mode]
   (choose-file stage, title, mode, nil))
  (^File [stage, title, mode, {:keys [file-types, initial-file, initial-directory], :or {file-types {"All Files" ["*.*"]}}}]
   (assert (contains? #{:open, :open-multiple, :save} mode) "mode must be one of the following: :open, :open-multiple, :save")
   (run-now
     (let [extension-filters (mapv #(apply extension-filter %) file-types)
           chooser (doto (FileChooser.)
                     (.setTitle title)
                     (-> .getExtensionFilters (.addAll ^Collection extension-filters))
                     (cond-> initial-directory (.setInitialDirectory (io/file initial-directory)))
                     (cond-> initial-file (.setInitialFileName initial-file)))]
       (when-let [^File file (case mode
                               :open (.showOpenDialog chooser, stage)
                               :open-multiple (.showOpenMultipleDialog chooser, stage)
                               :save (.showSaveDialog chooser, stage))]
         (if (= mode :save)
           (let [filename (.getAbsolutePath file),
                 extensions (-> chooser .getSelectedExtensionFilter .getExtensions)]
             (if (contains? (file-extension-set chooser) (u/file-extension filename))
               file
               (io/file (str filename "." (-> extensions first u/file-extension)))))
           file))))))


(defn remove-transparency
  "Creates a copy of the image that contains no transparency information.
  This is need for JPEG export via ImageIO in Java 8 (maybe resolved from Java 9 on)."
  ^BufferedImage [^BufferedImage image]
  (let [copy (BufferedImage. (.getWidth image), (.getHeight image), BufferedImage/OPAQUE),
        g (.createGraphics copy)]
    (.drawImage g image, 0, 0, nil)
    (.dispose g)
    copy))


(defn save-image
  [^Image image, filename, ^String image-type]
  (ImageIO/write
    (remove-transparency (SwingFXUtils/fromFXImage image, nil)),
    image-type,
    (io/file filename)))


(defn scene-snapshot
  [^Scene scene]
  (run-now (.snapshot scene, nil)))


(defn snapshot-parameters
  [width, height]
  (doto (SnapshotParameters.)
    (.setViewport (Rectangle2D. 0, 0, width, height))
    (.setFill Color/TRANSPARENT)))


(def ^:private supported-extensions (set (ImageIO/getWriterFormatNames)))

(defn export-node
  [^Node node, file]
  (run-now
    (let [image (.snapshot node, nil, nil),
          ext (u/file-extension file)]
      (if (contains? supported-extensions ext)
        (save-image image, file, ext)
        (u/illegal-argument "Unsupported file format %s!" ext)))))


(defn setup-event-handling!
  [^Node node, event-handler-map]
  (reduce-kv
    handle-event!
    node
    event-handler-map))


(defn insets
  ^Insets [insets-spec]
  (when insets-spec
    (cond
      (instance? Insets insets-spec)
      insets-spec

      (= insets-spec :empty)
      Insets/EMPTY

      (number? insets-spec)
      (Insets. insets-spec)

      (sequential? insets-spec)
      (if (= (count insets-spec) 4)
        (let [[top, right, bottom, left] insets-spec]
          (Insets. top, right, bottom, left))
        (u/illegal-argument "Inset values for all 4 borders must be given in the insets specification!"))

      (map? insets-spec)
      (let [{:keys [top, right, bottom, left]} insets-spec]
        (Insets. (or top 0.0), (or right 0.0), (or bottom 0.0), (or left 0.0)))

      :else (u/illegal-argument "Unsupported argument for insets specification - type: %s" (type insets-spec)))))


(defn corner-radii
  [radii-spec]
  (when radii-spec
    (cond
      (instance? CornerRadii radii-spec)
      radii-spec

      (= radii-spec :empty)
      CornerRadii/EMPTY

      (number? radii-spec)
      (CornerRadii. radii-spec)

      (sequential? radii-spec)
      (cond
        (#{4 5} (count radii-spec))
        (let [[top, right, bottom, left, percent?] radii-spec]
          (CornerRadii. (or top 0.0), (or right 0.0), (or bottom 0.0), (or left 0.0), (boolean percent?)))

        (= (count radii-spec) 16)
        (let [[a, b, c, d, e, f, g, h] (mapv #(or % 0.0) (take 8 radii-spec))
              [a?, b?, c?, d?, e?, f?, g?, h?] (mapv boolean (take 8 (concat (drop 8 radii-spec) (repeat nil))))]
          (CornerRadii. a, b, c, d, e, f, g, h, a?, b?, c?, d?, e?, f?, g?, h?))

        :else
        (u/illegal-argument "Radii values for all 4 corners must be given in the radii specification!"))

      (map? radii-spec)
      (let [{:keys [top, right, bottom, left, percent?]} radii-spec]
        (CornerRadii. (or top 0.0), (or right 0.0), (or bottom 0.0), (or left 0.0), (boolean percent?)))

      :else (u/illegal-argument "Unsupported argument for radii specification - type: %s" (type radii-spec)))))


(defn background-fill
  [{:keys [color, radii, insets]}]
  (BackgroundFill.
    (col/to-color color),
    (corner-radii radii)
    (clj-jfx.core/insets insets)))


(defn background
  "Creates a background using the specified fills and images."
  [{:keys [fills, images]}]
  (Background.
    ^List (mapv background-fill fills),
    ^List images))


(defn corner-radii-vec
  [^CornerRadii radii]
  (when radii
    [(.getTopLeftHorizontalRadius radii)
     (.getTopLeftVerticalRadius radii)
     (.getTopRightVerticalRadius radii)
     (.getTopRightHorizontalRadius radii)
     (.getBottomRightHorizontalRadius radii)
     (.getBottomRightVerticalRadius radii)
     (.getBottomLeftVerticalRadius radii)
     (.getBottomLeftHorizontalRadius radii)
     (.isTopLeftHorizontalRadiusAsPercentage radii)
     (.isTopLeftVerticalRadiusAsPercentage radii)
     (.isTopRightVerticalRadiusAsPercentage radii)
     (.isTopRightHorizontalRadiusAsPercentage radii)
     (.isBottomRightHorizontalRadiusAsPercentage radii)
     (.isBottomRightVerticalRadiusAsPercentage radii)
     (.isBottomLeftVerticalRadiusAsPercentage radii)
     (.isBottomLeftHorizontalRadiusAsPercentage radii)]))


(defn insets-vec
  [^Insets insets]
  (when insets
    [(.getTop insets) (.getRight insets) (.getBottom insets) (.getLeft insets)]))


(defn background-fill-map
  [^BackgroundFill background-fill]
  (when background-fill
    {:color (.getFill background-fill)
     :radii (corner-radii-vec (.getRadii background-fill))
     :insets (insets-vec (.getInsets background-fill))}))


(defn background-map
  [^Background background]
  (when background
    {:fills (mapv background-fill-map (.getFills background))
     :images (.getImages background)}))


(defn background-color!
  [^Region region, color]
  (doto region
    (.setBackground
      (when color
        (Background. ^"[Ljavafx.scene.layout.BackgroundFill;" (into-array [(BackgroundFill. (col/to-color color), CornerRadii/EMPTY, Insets/EMPTY)]))))))


(defn label-text!
  [^Label label, text]
  (doto label
    (.setText text)))


(defn shape-fill!
  ^Shape [^Shape shape, paint]
  (doto shape
    (.setFill (col/to-color paint))))


(defn shape-fill
  ^Paint [^Shape shape]
  (.getFill shape))


(defn shape-stroke!
  [^Shape shape, paint, ^double width]
  (doto shape
    (.setStroke (col/to-color paint))
    (.setStrokeWidth width)))


(defn border-stroke
  ^BorderStrokeStyle [stroke]
  (when stroke
    (cond
      (keyword? stroke)
      (case stroke
        :dashed BorderStrokeStyle/DASHED
        :dotted BorderStrokeStyle/DOTTED
        :solid BorderStrokeStyle/SOLID
        :none BorderStrokeStyle/NONE)
      (instance? BorderStrokeStyle stroke)
      stroke
      :else (u/illegal-argument "Unsupported argument for border-stroke - type: %s" (type stroke)))))


(defn border-corner-radii
  ^CornerRadii [radii-spec]
  (when radii-spec
    (cond
      (instance? CornerRadii radii-spec)
      radii-spec
      (number? radii-spec)
      (CornerRadii. radii-spec)
      (sequential? radii-spec)
      (if (= (count radii-spec) 4)
        (let [[top-left, top-right, bottom-right, bottom-left] radii-spec]
          (CornerRadii. top-left, top-right, bottom-right, bottom-left, false))
        (u/illegal-argument "Radius values for all 4 borders must be given in the border corner radii specification!"))
      (map? radii-spec)
      (let [{:keys [top-left, top-right, bottom-right, bottom-left]} radii-spec]
        (CornerRadii. (or top-left 0.0), (or top-right 0.0), (or bottom-right 0.0), (or bottom-left 0.0), false))
      :else (u/illegal-argument "Unsupported argument for border corner radii specification - type: %s" (type radii-spec)))))


(defn border-widths
  ^BorderWidths [widths-spec]
  (when widths-spec
    (cond
      (instance? BorderWidths widths-spec)
      widths-spec
      (number? widths-spec)
      (BorderWidths. widths-spec)
      (sequential? widths-spec)
      (if (= (count widths-spec) 4)
        (let [[top, right, bottom, left] widths-spec]
          (BorderWidths. top, right, bottom, left))
        (u/illegal-argument "Border width values for all 4 borders must be given in the border widths specification!"))
      (map? widths-spec)
      (let [{:keys [top, right, bottom, left]} widths-spec]
        (BorderWidths. (or top 0.0), (or right 0.0), (or bottom 0.0), (or left 0.0)))
      :else (u/illegal-argument "Unsupported argument for border widths specification - type: %s" (type widths-spec)))))


(defn region-border!
  "Configure the border of the region.
  color: color name string OR map {:keys [color, opacity]}
  widths: single width value OR vector [top, right, bottom, left] OR map {:keys [top, right, bottom, left]}
  corner-radii: single value OR vector [top-left, top-right, bottom-right, bottom-left] OR map {:keys [top-left, top-right, bottom-right, bottom-left]}
  stroke: one of :solid, :dashed, :dotted, :none"
  [^Region region, {:keys [color, widths, stroke, corner-radii]}]
  (let [bstroke (BorderStroke. (col/to-color color), (border-stroke stroke), (border-corner-radii corner-radii), (border-widths widths))]
    (doto region
      (.setBorder (Border. ^"[Ljavafx.scene.layout.BorderStroke;" (into-array [bstroke]))))))


(defn padding!
  [^Region region, insets-spec]
  (doto region
    (.setPadding (insets insets-spec))))


(defn spacing!
  [region, size]
  (cond
    (instance? VBox region) (.setSpacing ^VBox region, size)
    (instance? HBox region) (.setSpacing ^HBox region, size))
  region)


(defn vbox-margin!
  [^Node node, insets-spec]
  (doto node
    (VBox/setMargin (insets insets-spec))))


(defn hbox-margin!
  [^Node node, insets-spec]
  (doto node
    (HBox/setMargin (insets insets-spec))))


(defn row-span!
  [^Node node, ^long span]
  (doto node
    (GridPane/setRowSpan (int span))))


(defn column-span!
  [^Node node, span]
  (doto node
    (GridPane/setColumnSpan (int span))))


(defn to-text-alignment
  ^TextAlignment [alignment]
  (case alignment
    :left TextAlignment/LEFT,
    :right TextAlignment/RIGHT,
    :center TextAlignment/CENTER,
    :justify TextAlignment/JUSTIFY))


(defn text-alignment!
  "Set the alignment of the text (:left, :right, :center, :justify) of the given labeled node."
  [^Labeled labeled-node, alignment]
  (doto labeled-node
    (.setTextAlignment (to-text-alignment alignment))))


(defn to-alignment
  ^Pos [alignment]
  (case alignment
    :baseline-center Pos/BASELINE_CENTER,
    :baseline-left Pos/BASELINE_LEFT,
    :baseline-right Pos/BASELINE_RIGHT,
    :bottom-center Pos/BOTTOM_CENTER,
    :bottom-left Pos/BOTTOM_LEFT,
    :bottom-right Pos/BOTTOM_RIGHT,
    :center Pos/CENTER
    :center-left Pos/CENTER_LEFT,
    :center-right Pos/CENTER_RIGHT,
    :top-center Pos/TOP_CENTER,
    :top-left Pos/TOP_LEFT
    :top-right Pos/TOP_RIGHT))

(defn alignment!
  "Set alignment of graphic and text within the labeled node."
  [node, alignment]
  (cond
    (instance? Labeled node)
    (doto ^Labeled node
      (.setAlignment (to-alignment alignment)))
    (instance? VBox node)
    (doto ^VBox node
      (.setAlignment (to-alignment alignment)))
    (instance? HBox node)
    (doto ^HBox node
      (.setAlignment (to-alignment alignment)))
    :else (u/illegal-argument "Unsupported class %s" (.getCanonicalName (class node)))))


(def font-weight-map
  {:black FontWeight/BLACK,
   :bold FontWeight/BOLD,
   :extra-bold FontWeight/EXTRA_BOLD,
   :extra-light FontWeight/EXTRA_LIGHT,
   :light FontWeight/LIGHT,
   :medium FontWeight/MEDIUM,
   :normal FontWeight/NORMAL,
   :semi-bold FontWeight/SEMI_BOLD,
   :thin FontWeight/THIN})


(defn font-weight
  ^FontWeight [weight]
  (when weight
    (or
      (get font-weight-map weight)
      (u/illegal-argument "unknown font weight: %s" (pr-str weight)))))


(defn font-posture
  ^FontPosture [posture]
  (when posture
    (case posture
      :regular FontPosture/REGULAR,
      :italic FontPosture/ITALIC)))


(defn font
  [font-map-or-font]
  (when font-map-or-font
    (if (instance? Font font-map-or-font)
      font-map-or-font
      (let [{:keys [family, weight, posture, size]} font-map-or-font]
        (Font/font family, (font-weight weight), (font-posture posture), (or size -1))))))


(defn font-map
  [^Font font]
  (when font
    (let [style (.getStyle font)
          style-set (-> style str/lower-case (str/split #" ")
                      (->> (map keyword) set))]
      {:family (.getFamily font),
       :weight (or
                 (some style-set (keys font-weight-map))
                 :normal),
       :posture (get style-set :italic :regular),
       :size (.getSize font)})))


(defn text-font!
  [^Labeled labeled-node, font-spec]
  (doto labeled-node
    (.setFont (font font-spec))))


(defn width
  ^double [^Node node]
  (-> node .getLayoutBounds .getWidth))


(defn height
  ^double [^Node node]
  (-> node .getLayoutBounds .getHeight))


(defn size
  [^Node node]
  (let [bounds (.getLayoutBounds node)]
    {:width (.getWidth bounds),
     :height (.getHeight bounds)}))


(defn grow-always-in-grid!
  [^Node node]
  (doto node
    (GridPane/setVgrow Priority/ALWAYS)
    (GridPane/setHgrow Priority/ALWAYS)))


(defn grow-always-in-hbox!
  [^Node node]
  (doto node
    (HBox/setHgrow Priority/ALWAYS)))


(defn grow-always-in-vbox!
  [^Node node]
  (doto node
    (VBox/setVgrow Priority/ALWAYS)))


(defn layout!
  [^Parent parent-node]
  (doto parent-node
    .layout))


(defn row-index
  ^long [^Node node]
  (GridPane/getRowIndex node))


(defn column-index
  ^long [^Node node]
  (GridPane/getColumnIndex node))


(defn layout-bounds
  [^Node node]
  (let [bounds (.getLayoutBounds node)]
    [(.getWidth bounds) (.getHeight bounds)]))


(defn layout-position
  [^Node node]
  [(.getLayoutX node), (.getLayoutY node)])


(defn autosize
  [^Node node]
  (doto node
    .autosize))


(defn add-child
  [^Pane pane, child]
  (doto pane
    (-> .getChildren (.add child))))


(defn add-children
  [^Pane pane, children]
  (doto pane
    (-> .getChildren (.addAll ^Collection children))))


(defn add-to-toolbar
  [^ToolBar toolbar, ^Node node]
  (doto toolbar
    (-> .getItems (.add node))))


(defn has-child?
  [^Pane pane, child]
  (-> pane .getChildren (.contains child)))


(defn children-count
  [^Pane pane]
  (-> pane .getChildren .size))


(defn add-to-grid
  [^GridPane grid-pane, row, column, ^Node node]
  (doto grid-pane
    (.add node, column, row)))


(defn children
  [^Pane pane]
  (.getChildren pane))


(defn remove-children
  [^Pane pane, & children]
  (when (seq children)
    (-> pane .getChildren (.removeAll ^Collection children)))
  pane)


(defn remove-children-by-indices
  [^Pane pane, from, to]
  (doto pane
    (-> .getChildren (.remove from, to))))


(defn clear-children
  [^Pane pane]
  (doto pane
    (-> .getChildren .clear)))


(defn shift-rows
  [^GridPane gridpane, ^long shift]
  (doseq [^Node child (.getChildren gridpane)]
    (let [row-index (GridPane/getRowIndex child)]
      (GridPane/setRowIndex child, (int (+ row-index shift)))))
  gridpane)


(defn clear-row-constraints
  [^GridPane gridpane]
  (doto gridpane
    (-> .getRowConstraints .clear)))


(defn mouse-transparent!
  [^Node node, transparent?]
  (doto node
    (.setMouseTransparent transparent?)))


(defn context-menu
  [caption-handler-pairs]
  (->> caption-handler-pairs
    (reduce
      (fn [menu-items, menu-item-spec]
        (conj menu-items
          (if (= menu-item-spec :separator)
            (SeparatorMenuItem.)
            (let [[caption, handler, id] menu-item-spec]
              (doto (MenuItem. caption)
                (cond-> id (.setId id))
                (.setOnAction (event-handler handler)))))))
      [])
    ^"[Ljavafx.scene.control.MenuItem;" (into-array)
    (ContextMenu.)))


(defn set-context-menu
  [^Control control, ^ContextMenu menu]
  (doto control
    (.setContextMenu menu)))


(defn modal-window
  (^Stage [title, root-control]
   (modal-window title, root-control, nil, nil))
  (^Stage [title, root-control, width, height]
   (doto (Stage.)
     (.setTitle title)
     (.setScene (if (and width height) (Scene. root-control, width, height) (Scene. root-control)))
     (.initModality Modality/APPLICATION_MODAL))))


(defn window
  (^Stage [title, root-control]
   (window title, root-control, nil, nil))
  (^Stage [title, root-control, width, height]
   (doto (Stage.)
     (.setTitle title)
     (.setScene (if (and width height) (Scene. root-control, width, height) (Scene. root-control))))))


(defn close
  [^Stage stage]
  (run-now
    (doto stage
      .close)))


(defn button
  [{:keys [text, handler, min-width, min-height, default?, cancel?, disable?]}]
  (doto (Button.)
    (cond-> text (.setText text))
    (cond-> handler (handle-event! :action handler))
    (cond-> min-width (min-width! min-width))
    (cond-> min-height (min-height! min-height))
    (cond-> disable? (-> .disableProperty (.bind disable?)))
    (.setDefaultButton (boolean default?))
    (.setCancelButton (boolean cancel?))))


(defn prevent-exit!
  "Prevents that the window can be closed by the os close button [x]."
  [^Stage window]
  (doto window
    (handle-event! :window-close-request
      (fn [^WindowEvent e]
        (.consume e)))))


(defn dialog
  "Creates a dialog window with buttons for ok and cancel.
  The button texts and handlers can be specified."
  (^Stage [title, control]
   (dialog title, control, nil))
  (^Stage [title, control, {:keys [width, height, modal?, ok-button, cancel-button, close-handler, prevent-exit?]}]
   (let [exit-by-button? (atom false)
         window (doto (Stage.)
                  (.setTitle title)
                  (handle-event! :window-hidden
                    (fn [event]
                      (if close-handler
                        (close-handler event)
                        (when-let [cancel-handler (:handler cancel-button)]
                          (cancel-handler event)))))
                  (cond->
                    modal? (doto
                             (.initModality Modality/APPLICATION_MODAL))
                    prevent-exit? (handle-event! :window-close-request
                                    (fn [^WindowEvent e]
                                      (when-not (deref exit-by-button?)
                                        (.consume e))))))
         create-button (fn [button-settings, button-type]
                         (let [{:keys [text, handler] :as button-settings} (if (map? button-settings) button-settings {})]
                           (button
                             (assoc button-settings
                               :text (or text
                                       (case button-type
                                         :ok "Ok"
                                         :cancel "Cancel"))
                               :handler (if handler
                                          (fn [event]
                                            (handler event)
                                            (reset! exit-by-button? true)
                                            (close window))
                                          (fn [event]
                                            (reset! exit-by-button? true)
                                            (close window)))
                               :default? (= button-type :ok)
                               :cancel? (= button-type :cancel)))))
         button-hbox (doto (HBox.)
                       (spacing! 10.0)
                       (add-children
                         (cond-> [(doto (Region.)
                                    (HBox/setHgrow Priority/ALWAYS)
                                    (max-width! Double/MAX_VALUE))],
                           ok-button (conj (create-button ok-button, :ok)),
                           cancel-button (conj (create-button cancel-button, :cancel)))))
         root (doto (VBox.)
                (padding! 10.0)
                (spacing! 10.0)
                (add-children [(grow-always-in-vbox! control), button-hbox]))]
     (doto window
       (.setScene (if (and width height) (Scene. root, width, height) (Scene. root)))
       (cond->
         width (doto (.setWidth width))
         height (doto (.setHeight height)))
       (cond-> (and (nil? width) (nil? height)) (size-to-scene!))))))


(defn user-data!
  "Sets the user data of a window."
  [^Window window, data]
  (doto window
    (.setUserData data)))


(defn user-data
  "Returns the user data of a window."
  [^Window window]
  (.getUserData window))


(defn window-style!
  [^Stage window, style]
  (let [^StageStyle style (if (instance? StageStyle style)
                            style
                            (case style
                              :undecorated StageStyle/UNDECORATED,
                              :decorated StageStyle/DECORATED,
                              :transparent StageStyle/TRANSPARENT,
                              :utility StageStyle/UTILITY,
                              :unified StageStyle/UNIFIED))]
    (doto window
      (.initStyle style))))


(defn center-on-screen!
  [^Stage stage]
  (doto stage
    .centerOnScreen))


(defn resizable!
  [^Stage stage, resizable?]
  (doto stage
    (.setResizable resizable?)))


(defn show-and-wait
  [^Stage stage]
  (doto stage
    .showAndWait))


(defn show
  [^Stage stage]
  (doto stage
    .show))


(defn shown?
  [^Stage stage]
  (.isShowing stage))


(defn hide
  [^Stage stage]
  (doto stage
    .hide))

(defn implicit-exit!
  "If set to true, the JavaFX runtime will shutdown when the last stage is closed."
  [enable?]
  (Platform/setImplicitExit enable?))


(defn selected-index
  ^long [^ComboBox combobox]
  (-> combobox .getSelectionModel .getSelectedIndex))



(defn invalidation-listener!
  "Adds the given function or change listener to the given observable value."
  [^Observable observable, listener-or-fn]
  (let [^InvalidationListener listener (cond
                                         (instance? InvalidationListener listener-or-fn)
                                         listener-or-fn
                                         (fn? listener-or-fn)
                                         (reify InvalidationListener
                                           (invalidated [this, observable]
                                             (listener-or-fn observable)))
                                         :else (u/illegal-argument "Expects an InvalidationListener or function"))]
    (.addListener observable listener)
    listener))


(defn change-listener!
  "Adds the given function or change listener to the given observable value."
  [^ObservableValue observable-value, listener-or-fn]
  (let [^ChangeListener listener (cond
                                   (instance? ChangeListener listener-or-fn)
                                   listener-or-fn
                                   (fn? listener-or-fn)
                                   (reify ChangeListener
                                     (changed [this, observable, old-value, new-value]
                                       (listener-or-fn observable, old-value, new-value)))
                                   :else (u/illegal-argument "Expects an ChangeListener or function"))]
    (.addListener observable-value listener)
    listener))



(defn bind-to-atom
  "Returns an atom that is update when the given property is changed.
  A handler function (fn [old-value, new-value] ...) can be specified
  which is call with the old and the new value when the property is changed.
  A modification function (fn [old-value, new-value] ...) can be specified
  which is called with the old and the new value and whose return value replaces the previous new value."
  ([property]
   (bind-to-atom property, nil, nil))
  ([property, handler-fn]
   (bind-to-atom property, handler-fn, nil))
  ([^ObservableValue property, handler-fn, modification-fn]
   (let [value (atom (.getValue property))]
     (change-listener! property,
       (fn [observable-value, old-value, new-value]
         (u/safe
           (let [value-to-use (cond->> new-value modification-fn (modification-fn old-value))]
             (reset! value value-to-use)
             (when-not (= value-to-use new-value)
               (if (instance? WritableValue observable-value)
                 (.setValue ^WritableValue observable-value value-to-use)
                 (println "WARNING: a modification function has been given but the bound property is not writable!")))
             (when handler-fn
               (handler-fn old-value, new-value))))))
     value)))


(deftype PropertyInRef [data-ref, modify!, value-path, expression-helper]

  Property

  (^void addListener [this, ^InvalidationListener listener]
    (let [^ExpressionHelper helper @expression-helper
          helper (ExpressionHelper/addListener helper, this, listener)]
      (reset! expression-helper helper)))

  (^void removeListener [this, ^InvalidationListener listener]
    (let [^ExpressionHelper helper @expression-helper
          helper (ExpressionHelper/removeListener helper, listener)]
      (reset! expression-helper helper)))

  (^void addListener [this, ^ChangeListener listener]
    (let [^ExpressionHelper helper @expression-helper
          helper (ExpressionHelper/addListener helper, this, listener)]
      (reset! expression-helper helper)))

  (^void removeListener [this, ^ChangeListener listener]
    (let [^ExpressionHelper helper @expression-helper
          helper (ExpressionHelper/removeListener helper, listener)]
      (reset! expression-helper helper)))

  (getValue [this]
    (get-in (deref data-ref) value-path))

  (setValue [this, value]
    ; just set the new value, the watch on the ref/atom takes care of notification
    (modify! assoc-in value-path value))

  (getName [this]
    (str value-path))

  (getBean [this]
    nil)

  (bindBidirectional [this, observable]
    (Bindings/bindBidirectional this, observable))

  (unbindBidirectional [this, observable]
    (Bindings/unbindBidirectional this, observable))

  Object
  (toString [this]
    (str (.getValue this))))


(defn determine-alter-value-fn
  [atom-or-ref]
  (cond
    (instance? clojure.lang.Atom atom-or-ref) (fn modify-atom [f, & args] (apply swap! atom-or-ref, f, args))
    (instance? clojure.lang.Ref atom-or-ref) (fn modify-ref [f, & args] (dosync (apply alter atom-or-ref f args)))
    :else (u/illegal-argument "Neither an atom nor a reference: %s" (type atom-or-ref))))


(defn property-in-ref
  ^Property [data-ref, value-path]
  (PropertyInRef. data-ref, (determine-alter-value-fn data-ref), value-path, (atom nil)))


(defn fire-value-changed
  [^PropertyInRef property]
  (ExpressionHelper/fireValueChangedEvent (deref (.expression-helper property))))


(defn notify-listeners
  [^PropertyInRef property, old-state, new-state]
  (let [value-path (.value-path property),
        old-value (get-in old-state value-path),
        new-value (get-in new-state value-path)]
    ; notification needed?
    (when-not (= old-value new-value)
      ; change listeners
      (fire-value-changed property))))


(defn text-input
  [title, header, query-text]
  (run-now
    (let [dialog (doto (TextInputDialog.)
                   (.setTitle title)
                   (.setHeaderText header)
                   (.setContentText query-text)),
          result (.showAndWait dialog)]
      (when (.isPresent result)
        (.get result)))))



(defn setup-combobox
  "Prepares a combobox"
  [^ComboBox combobox, options]
  (doto combobox
    (.setItems (FXCollections/observableArrayList ^objects (into-array options)))))


(defn setup-combobox-first-selected!
  "Prepares a combobox"
  (^ComboBox [parent, combobox-id, options]
   (setup-combobox-first-selected! (find-node parent, combobox-id), options))
  (^ComboBox [^ComboBox combobox, options]
   (doto combobox
     (setup-combobox options)
     (-> .getSelectionModel (.select 0)))))


(defn setup-combobox-unselected!
  "Prepares a combobox"
  ([^ComboBox combobox, options, prompt-text]
   (doto combobox
     (.setItems (FXCollections/observableArrayList ^objects (into-array options)))
     (.setPromptText prompt-text)
     (-> .getSelectionModel .clearSelection)))
  ([parent, combobox-id, options, prompt-text]
   (setup-combobox-unselected! (find-node parent, combobox-id), options, prompt-text)))


(defn selected-item
  [control]
  (cond
    (instance? ComboBox control) (-> ^ComboBox control .getSelectionModel .getSelectedItem)
    (instance? ListView control) (-> ^ListView control .getSelectionModel .getSelectedItem)
    :else (u/illegal-argument "Unsupported control of type %s" (.getCanonicalName (class control)))))


(defn selected?
  [^CheckBox checkbox]
  (.isSelected checkbox))


(defn selected-color!
  [^ColorPicker color-picker, color]
  (doto color-picker
    (.setValue (col/to-color color))))


(defn selected-color
  ^Color [^ColorPicker color-picker]
  (.getValue color-picker))


(defn titled-pane-content
  [^TitledPane titled-pane]
  (.getContent titled-pane))


(defn text-field-text!
  [^TextField text-field, value]
  (doto text-field
    (.setText value)))


(defn text-field-text
  [^TextField text-field]
  (.getText text-field))


(defn text-field-format!
  [^TextField text-field, ^NumberFormat format, predicate?]
  (doto text-field
    (.setTextFormatter
      (TextFormatter.
        (reify UnaryOperator
          (apply [this, change]
            (let [^TextFormatter$Change change change
                  new-text (.getControlNewText change)]
              (if (.isEmpty new-text)
                change
                (let [parse-pos (ParsePosition. 0),
                      parsed (.parse format new-text, parse-pos)]
                  (when-not (or (nil? parsed) (< (.getIndex parse-pos) (count new-text)))
                    (when (predicate? parsed)
                      change)))))))))))


(defn decimal-format
  ([]
   (decimal-format Locale/ENGLISH))
  ([^Locale locale]
   (doto (DecimalFormat/getInstance locale)
     (.setGroupingUsed false))))


(defn integer-format
  ([]
   (integer-format Locale/ENGLISH))
  ([^Locale locale]
   (doto (NumberFormat/getIntegerInstance locale)
     (.setGroupingUsed false))))


(defn circle
  [^double radius, color]
  (Circle. radius, (col/to-color color)))


(defn text
  [{:keys [text], font-config :font}]
  (doto (Text. text)
    (cond->
      font
      (.setFont (font font-config)))))


(defn update-text
  [^Text text-node, {:keys [text], font-config :font}]
  (doto text-node
    (.setText text)
    (cond->
      font (.setFont (font font-config)))))


(defn textflow-texts!
  [^TextFlow textflow, text-map-list]
  (let [texts (.getChildren textflow),
        current-texts-count (count texts),
        new-texts-count (count text-map-list),
        delta (- new-texts-count current-texts-count)]
    (cond
      (pos? delta) (.addAll texts ^objects (into-array (repeatedly delta #(Text.))))
      (neg? delta) (.remove texts new-texts-count current-texts-count))
    (doseq [[text-node, text-map] (mapv vector texts text-map-list)]
      (update-text text-node, text-map))
    textflow))


(defn textflow-texts
  [^TextFlow textflow]
  (mapv (fn [^Text text] (.getText text)) (.getChildren textflow)))


(defn textflow-textalignment!
  [^TextFlow textflow, alignment]
  (doto textflow
    (.setTextAlignment (to-text-alignment alignment))))


(defn textflow-color!
  [^TextFlow textflow, color]
  (let [color (col/to-color color)]
    (doseq [^Text text (.getChildren textflow)]
      (.setStroke text, color)))
  textflow)


(defn textflow
  [& texts]
  (doto (TextFlow.)
    (add-children texts)))


(defn text-control
  [^String content]
  (doto (Text. content)
    (.setFontSmoothingType FontSmoothingType/LCD)))



(defn stage
  [^Node node]
  (-> node .getScene .getWindow))








(defn stringify
  [x]
  (if (instance? clojure.lang.Named x)
    (name x)
    (str x)))


(defn observable-value?
  [x]
  (instance? ObservableValue x))


(defn property?
  [x]
  (instance? Property x))

(defn property-bound?
  [property]
  (.isBound ^Property property))


(defn register-property
  [derefable-map, property-map-path, property]
  (doto derefable-map
    (alter-meta! assoc ::property-source true)
    (alter-meta! assoc-in [::property-map property-map-path] property)))


(defn lookup-property
  [derefable-map, property-map-path]
  (let [meta-map (meta derefable-map)]
    (when (::property-source meta-map)
      (get-in meta-map [::property-map property-map-path]))))


(defn map-entry-property
  [derefable-map, property-map-path]
  (let [property-map-path (if (vector? property-map-path) property-map-path [property-map-path])]
    (if-let [property (lookup-property derefable-map, property-map-path)]
      property
      (let [property-key (-> (str/join "-" (map stringify property-map-path)) (str "-" (u/uuid-str)) keyword),
            alter-value-fn (determine-alter-value-fn derefable-map),
            set-value! (fn [value]
                         (alter-value-fn assoc-in property-map-path value)),
            get-value (fn [m] (get-in m property-map-path)),
            caller (atom nil)
            property (doto (SimpleObjectProperty. (get-value (deref derefable-map)))
                       (.addListener
                         (reify ChangeListener
                           (changed [_, _, old-value, new-value]
                             (when-not (or (= @caller :ref-listener) (= old-value new-value))
                               (reset! caller :prop-listener)
                               (try
                                 (set-value! new-value)
                                 (finally
                                   (reset! caller nil))))))))]
        ; add watch to promote changes from the derefable-map to the property
        (add-watch derefable-map property-key
          (fn [_, _, old-map, new-map]
            (let [old-value (get-value old-map),
                  new-value (get-value new-map)]
              (when-not (or (= @caller :prop-listener) (= old-value new-value) (property-bound? property))
                (run-now
                  (reset! caller :ref-listener)
                  (try
                    (.setValue property new-value)
                    (finally
                      (reset! caller nil))))))))
        ; register property to prevent garbage collection and for efficient reuse
        (register-property derefable-map, property-map-path, property)
        property))))


(defn map-property
  ([derefable-map]
   (map-property derefable-map, nil, nil))
  ([derefable-map, property-set]
   (map-property derefable-map, property-set, nil))
  ([derefable-map, property-set, prefix]
   (let [property-set (when property-set
                        (if (set? property-set) property-set (set property-set))),
         prefix (when prefix
                  (if (sequential? prefix) prefix (vector prefix))),
         project-fn (if property-set
                      (if prefix
                        #(select-keys (get-in % prefix) property-set)
                        #(select-keys % property-set))
                      (if prefix
                        #(get-in % prefix)
                        identity))]
     (if-let [property (lookup-property derefable-map, property-set)]
       property
       (let [property-key (-> (str/join "-" (map stringify property-set)) (str "-" (u/uuid-str)) keyword),
             alter-value-fn (determine-alter-value-fn derefable-map),
             set-value! (if property-set
                          (if prefix
                            (fn [value]
                              (alter-value-fn update-in prefix merge (project-fn value)))
                            (fn [value]
                              (alter-value-fn merge (project-fn value))))
                          (if prefix
                            (fn [value]
                              (alter-value-fn assoc-in prefix (project-fn value)))
                            (fn [value]
                              (alter-value-fn (constantly (project-fn value)))))),
             get-value (fn [m] (project-fn m)),
             caller (atom nil)
             property (doto (SimpleObjectProperty. (get-value (deref derefable-map)))
                        (.addListener
                          (reify ChangeListener
                            (changed [_, _, old-value, new-value]
                              (when-not (or (= @caller :ref-listener) (= old-value new-value))
                                (reset! caller :prop-listener)
                                (try
                                  (set-value! new-value)
                                  (finally
                                    (reset! caller nil))))))))]
         ; add watch to promote changes from the derefable-map to the property
         (add-watch derefable-map property-key
           (fn [_, _, old-map, new-map]
             (let [old-value (get-value old-map),
                   new-value (get-value new-map)]
               (when-not (or (= @caller :prop-listener) (= old-value new-value) (property-bound? property))
                 (run-now
                   (reset! caller :ref-listener)
                   (try
                     (.setValue property new-value)
                     (finally
                       (reset! caller nil))))))))
         ; register property to prevent garbage collection and for efficient reuse
         (register-property derefable-map, property-set, property)
         property)))))


(defn object-property
  [value]
  (SimpleObjectProperty. value))


(defn ->property
  [atom-or-ref]
  (let [alter-fn (determine-alter-value-fn atom-or-ref)
        id (System/identityHashCode atom-or-ref)]
    (if-let [property (lookup-property atom-or-ref, id)]
      property
      (let [caller (atom nil)
            property (doto (SimpleObjectProperty. (deref atom-or-ref))
                       (.addListener
                         (reify ChangeListener
                           (changed [_, _, old-value, new-value]
                             (when-not (or (= @caller :ref-listener) (= old-value new-value))
                               (reset! caller :prop-listener)
                               (try
                                 (alter-fn (constantly new-value))
                                 (finally
                                   (reset! caller nil))))))))]
        ; add watch to promote changes from the derefable-map to the property
        (add-watch atom-or-ref id
          (fn [_, _, old-value, new-value]
            (when-not (or (= @caller :prop-listener) (= old-value new-value) (property-bound? property))
              (run-now
                (reset! caller :ref-listener)
                (try
                  (.setValue property new-value)
                  (finally
                    (reset! caller nil)))))))
        ; register property to prevent garbage collection and for efficient reuse
        (register-property atom-or-ref, id, property)
        property))))


(defn bind
  [^Property property, ^ObservableValue observable-value]
  (doto property
    (.bind observable-value)))


(defn bind-updating
  [^Property property, ^ObservableValue observable-value, update-fn]
  (when (nil? observable-value)
    (throw (NullPointerException. "Cannot bind to null")))

  (when (.isBound property)
    (.unbind property))

  (let [update-value (fn [old-observable-value, new-observable-value]
                       (.setValue property
                         (update-fn (.getValue property), old-observable-value, new-observable-value)))]
    ; configure listener
    (change-listener! observable-value,
      (fn [_, old-value, new-value]
        (when-not (= old-value new-value)
          (update-value old-value, new-value))))
    ; update value
    (update-value nil, (.getValue observable-value)))

  property)


(defn bind-list-updating
  [^ObservableList target-list, ^ObservableValue observable-value, update-fn]
  (when (nil? observable-value)
    (throw (NullPointerException. "Cannot bind to null")))

  (let [update-list (fn [old-value, new-value]
                      (update-fn target-list, old-value, new-value))]
    ; set listener
    (change-listener! observable-value,
      (fn [_, old-value, new-value]
        (when-not (= old-value new-value)
          (update-list old-value, new-value))))
    ; update now
    (update-list nil, (.getValue observable-value)))
  ; return list
  target-list)


(defn unbind
  [^Property property]
  (doto property
    (.unbind)))


(defn bind-bidirectional
  [^Property to-property, ^Property from-property]
  (doto to-property
    (Bindings/bindBidirectional from-property)))


(defn determine-get-value
  [observable]
  (cond
    (instance? ObservableValue observable)
    (fn [] (.getValue ^ObservableValue observable))

    (instance? ObservableList observable)
    (fn [] (into [] observable))

    :error
    (u/illegal-argument "Observable must be either an ObservableValue or an ObservableList (%s)" (str (class observable)))))


(defn listen-to
  "The given function is called whenever the given observable values change."
  [f, & observable-values]
  (assert (every? #(instance? Observable %) observable-values) "listen-to can only be called on Observable instances.")
  (let [get-value-fns (mapv determine-get-value observable-values)
        listener (reify InvalidationListener
                   (invalidated [_, _]
                     ; apply f to the current values of all observables
                     (apply f (map #(%) get-value-fns))))]
    (doseq [^Observable ov observable-values]
      (.addListener ov listener))
    listener))


(defn invalidate!
  [^InvalidationListener listener]
  (doto listener
    (.invalidated nil)))


(defn property-name->symbol
  [property-name]
  (let [segments (-> property-name name (str/split #"-")),
        camel-case (->> segments
                     rest
                     (map str/capitalize)
                     (list* (first segments))
                     (apply str))]
    (symbol (str camel-case "Property"))))


(defmacro property
  [x, property-name]
  (let [property-getter (property-name->symbol property-name)]
    `(. ~x ~property-getter)))


(defn value!
  [^WritableValue writable, value]
  (doto writable
    (.setValue value)))


(defn value
  [^ObservableValue observable]
  (.getValue observable))


(defmacro property-value!
  [x, property-name, value]
  `(try
     (value! (property ~x, ~property-name), ~value)
     (catch Throwable t#
       (throw (RuntimeException. ~(format "Property %s could not be set." (name property-name)) t#)))))


(defmacro property-value
  [x, property-name]
  `(value (property ~x, ~property-name)))


(defn functional-property
  "Creates a property that is calculated via the given function based on the observable values.
  Note: the returned property can be garbage collected if it is not referenced anywhere or not used in another binding.
  Then listeners on the property will not be called anymore."
  [f, & observable-values]
  (assert (every? #(instance? Observable %) observable-values) "Functional property can only depend on Observable instances.")
  (let [get-value-fns (mapv determine-get-value observable-values)]
    (Bindings/createObjectBinding
      (fn []
        (apply f (map #(%) get-value-fns)))
      (into-array Observable observable-values))))


(defn functional-property-bidirectional
  ^Property [f, f-1, ^Property property]
  (let [func-prop (SimpleObjectProperty. (f (value property)))]
    (change-listener! property
      (fn [_, old-value, new-value]
        (when-not (= old-value new-value)
          (value! func-prop (f new-value)))))
    (change-listener! func-prop
      (fn [_, old-value, new-value]
        (when-not (= old-value new-value)
          (value! property (f-1 new-value)))))
    func-prop))


(defn map-attribute-property
  "Creates a bidirectional bound property representing an attribute of a map property."
  [attribute-kw, ^Property map-property]
  (let [attribute-prop (SimpleObjectProperty. (get (value map-property) attribute-kw))
        caller (atom nil)]
    ; update: map -> attribute
    (change-listener! map-property
      (fn [_, old-map, new-map]
        (when-not (= @caller :attribute-prop-listener)
          (let [old-value (get old-map attribute-kw)
                new-value (get new-map attribute-kw)]
            (when-not (= old-value new-value)
              (reset! caller :map-prop-listener)
              (try
                (value! attribute-prop new-value)
                (finally
                  (reset! caller nil))))))))
    ; update: attribute -> map
    (change-listener! attribute-prop
      (fn [_, old-value, new-value]
        (when-not (or (= @caller :map-prop-listener) (= old-value new-value))
          (reset! caller :attribute-prop-listener)
          (try
            (value! map-property (assoc (value map-property) attribute-kw new-value))
            (finally
              (reset! caller nil))))))
    ; return
    attribute-prop))


(defn property->observable-list
  "Converts a property or observable value to an observable list.
  If a property is given, a bidirectional binding will be used.
  If an observable value is given, only a unidirection binding from observable value to observable list will be used (read only observable list).
  The property/observable values must be list values."
  [property]
  (assert (or (property? property) (observable-value? property)) "Property or observable value expected")
  (let [^List initial-elements (value property)
        ^ObservableList observable-list (cond-> (FXCollections/observableArrayList)
                                          (seq initial-elements) (doto (.addAll initial-elements))
                                          (not (property? property)) (FXCollections/unmodifiableObservableList))]
    (change-listener! property,
      (fn [_, old-value, new-value]
        (when-not (= old-value new-value)
          (if (nil? new-value)
            (.clear observable-list)
            (.setAll observable-list ^List new-value)))))
    (when (property? property)
      (invalidation-listener! observable-list,
        (fn [^ObservableList observable]
          (when-not (property-bound? property)
            (value! property (vec observable))))))
    observable-list))


(defn observable-array-list
  (^ObservableList []
   (FXCollections/observableArrayList))
  (^ObservableList [^Collection list]
   (FXCollections/observableArrayList list)))


(defn clear-list
  [^ObservableList list]
  (doto list
    .clear))


(defn add-to-list
  [^ObservableList list, & elements]
  (doto list
    (.addAll ^Collection elements)))


(defn observable->observable-list
  "Converts an observable to an observable list. The observable list is bound to the observable.
  The observable values must be list values."
  [observable]
  (let [^List initial-elements (value observable)
        ^ObservableList observable-list (cond-> (FXCollections/observableArrayList)
                                          (seq initial-elements) (doto (.addAll initial-elements)))]
    (change-listener! observable,
      (fn [_, old-value, new-value]
        (when-not (= old-value new-value)
          (if (nil? new-value)
            (.clear observable-list)
            (.setAll observable-list ^List new-value)))))
    observable-list))


(defn create-tooltip
  [value-or-observable]
  (if (instance? ObservableValue value-or-observable)
    (let [tooltip (Tooltip.)]
      (bind (property tooltip, :text) value-or-observable)
      tooltip)
    (Tooltip. (str value-or-observable))))


(defn tooltip!
  [^Control control, value-or-property]
  (if (or (nil? value-or-property) (and (string? value-or-property) (str/blank? value-or-property)))
    (doto control
      (.setTooltip nil))
    (doto control
      (.setTooltip
        (cond-> value-or-property
          (not (instance? Tooltip value-or-property)) create-tooltip)))))


(defn get-tooltip
  ^Tooltip [^Node node]
  (:tooltip (.getProperties node)))


(defn set-tooltip
  [^Node node, tooltip]
  (doto node
    (-> .getProperties (.put :tooltip tooltip))))


(defn install-tooltip!
  [^Node node, text]
  (if (str/blank? text)
    ; remove tooltip
    (do
      (set-tooltip node, nil)
      (doto node
        (Tooltip/install nil)))
    (if-let [current-tooltip (get-tooltip node)]
      (do
        (when-not (= (.getText current-tooltip) text)
          (.setText current-tooltip text))
        node)
      (let [new-tooltip (Tooltip. (str text))]
        (set-tooltip node, new-tooltip)
        (doto node
          (Tooltip/install new-tooltip))))))


(defn find-ids-in-fxml
  [fxml-filename]
  (->> fxml-filename
    io/resource
    slurp
    (re-seq #"\sid=\"(.+?)\"")
    (mapv (comp keyword second))
    (into (sorted-set))))


(defn find-child-nodes
  [parent-node]
  ; parent node overwrites children with same id
  (t/traverse-post-order
    ; init: empty map, combine: if value (assumption: map) then put it into map
    (fn
      ([] {})
      ([result, value]
       (if value
         (let [duplicate-ids (u/overlapping-keys result, value)]
           (when (seq duplicate-ids)
             (u/println-err (format "find-child-nodes - Found duplicate control ids: %s." (str/join ", " (map name duplicate-ids)))))
           (merge result value))
         result)
       (cond-> result value (merge value)))),
    (fn [node]
      ; when node is found, stop and return it
      (when-let [id (node-id node)]
        (hash-map (keyword id) node)))
    parent-node))


(defn create-control
  [fxml-filename]
  (let [control (load-control fxml-filename),
        children-ids (find-ids-in-fxml fxml-filename),
        children-map (find-child-nodes control),
        missing-ids (set/difference children-ids (set (keys children-map)))]
    (when (seq missing-ids)
      (u/println-err (format "create-control: When creating \"%s\" the child controls with the following ids were not found: %s" fxml-filename (str/join ", " (map name missing-ids)))))
    (ctrl/create control, children-map)))


(defn control-node
  [control]
  (ctrl/control-node control))


(defn control-children
  [control]
  (ctrl/control-children control))


(defn combobox-cell-factory!
  ([combobox, converter]
   (combobox-cell-factory! combobox, converter, nil))
  ([^ComboBox combobox, converter, cell-update-fn]
   (doto combobox
     (.setCellFactory
       (reify Callback
         (call [_, combobox]
           (GenericListCell. converter, cell-update-fn, nil)))))))


(defn update-list-cell-tooltip
  [tooltip-key, ^ListCell list-cell, item, empty]
  ; adjust tooltip
  (if (or empty (nil? tooltip-key))
    (tooltip! list-cell nil)
    (tooltip! list-cell (get item tooltip-key))))



(defn string-converter
  ([to-string-fn]
   (proxy [StringConverter] []
     (toString [value]
       (to-string-fn value))))
  ([to-string-fn, from-string-fn]
   (proxy [StringConverter] []
     (toString [value]
       (to-string-fn value))
     (fromString [s]
       (from-string-fn s)))))


(defn map-converter
  ^StringConverter [text-key]
  (proxy [StringConverter] []
    (toString [data-map]
      (get data-map text-key))))


(defn combobox-converter!
  [^ComboBox combobox, ^StringConverter converter]
  (doto combobox
    (.setConverter converter)))


(defprotocol SingleSelection
  (set-selected-index [selectable, index])
  (get-selected-index [selectable])
  (get-selected-index-property [selectable])
  (select-first [selectable])
  (select-item [selectable, item]))


(extend-protocol SingleSelection

  ComboBox
  (set-selected-index [^ComboBox combobox, index]
    (doto combobox
      (-> .getSelectionModel (.select (int index)))))
  (get-selected-index [^ComboBox combobox]
    (-> combobox .getSelectionModel .getSelectedIndex))
  (get-selected-index-property [^ComboBox combobox]
    (-> combobox .getSelectionModel (property :selected-index)))
  (select-first [^ComboBox combobox]
    (doto combobox
      (-> .getSelectionModel .selectFirst)))
  (select-item [^ComboBox combobox, item]
    (doto combobox
      (-> .getSelectionModel (.select item))))

  ListView
  (set-selected-index [^ListView listview, index]
    (doto listview
      (-> .getSelectionModel (.select (int index)))))
  (get-selected-index [^ListView listview]
    (-> listview .getSelectionModel .getSelectedIndex))
  (get-selected-index-property [^ListView listview]
    (-> listview .getSelectionModel (property :selected-index)))
  (select-first [^ListView listview]
    (doto listview
      (-> .getSelectionModel .selectFirst)))
  (select-item [^ListView listview, item]
    (doto listview
      (.scrollTo item)
      (-> .getSelectionModel (.select item))))

  TabPane
  (set-selected-index [^TabPane tabpane, index]
    (doto tabpane
      (-> .getSelectionModel (.select (int index)))))
  (get-selected-index [^TabPane tabpane]
    (-> tabpane .getSelectionModel .getSelectedIndex))
  (get-selected-index-property [^TabPane tabpane]
    (-> tabpane .getSelectionModel (property :selected-index)))
  (select-first [^TabPane tabpane]
    (doto tabpane
      (-> .getSelectionModel .selectFirst)))
  (select-item [^TabPane tabpane, item]
    (doto tabpane
      (-> .getSelectionModel (.select item))))

  TableView
  (set-selected-index [^TableView tableview, index]
    (doto tableview
      (-> .getSelectionModel (.select (int index)))))
  (get-selected-index [^TableView tableview]
    (-> tableview .getSelectionModel .getSelectedIndex))
  (get-selected-index-property [^TableView tableview]
    (-> tableview .getSelectionModel (property :selected-index)))
  (select-first [^TableView tableview]
    (doto tableview
      (-> .getSelectionModel .selectFirst)))
  (select-item [^TableView tableview, item]
    (doto tableview
      (-> .getSelectionModel (.select item)))))


(defn selected-index-property
  [single-selectable]
  (get-selected-index-property single-selectable))


(defn selected-index!
  [single-selectable, index]
  (try
    (set-selected-index single-selectable, index)
    (catch Throwable t
      (println (.getId ^Styleable single-selectable) "index =" index "selected =" (get-selected-index single-selectable))
      (throw t))))


(defn selected-index
  ^long [single-selectable]
  (get-selected-index single-selectable))


(defn select-first!
  [single-selectable]
  (select-first single-selectable))


(defn select-item!
  [single-selectable, item]
  (select-item single-selectable, item))


(defn setup-combobox!
  [^ComboBox combobox, ^ObservableList observable-item-list & {:keys [text-key, tooltip-key]}]
  (let [converter (when text-key (map-converter text-key))]
    (change-listener! (property combobox :items)
      (fn [_, old-items, new-items]
        (when-not (identical? old-items new-items)
          (selected-index! combobox -1))))
    (invalidation-listener! observable-item-list
      (fn [_]
        (selected-index! combobox -1)))
    (doto combobox
      (cond-> converter (combobox-converter! converter))
      (combobox-cell-factory! converter, (partial update-list-cell-tooltip tooltip-key))
      (-> (property :items) (value! observable-item-list)))))


(defn listview-cell-factory!
  [^ListView listview, {:keys [converter, cell-update-fn, click-handler]}]
  (doto listview
    (.setCellFactory
      (reify Callback
        (call [_, listview]
          (GenericListCell. converter, cell-update-fn, click-handler))))))



(defn setup-listview!
  [^ListView listview, ^ObservableList observable-item-list, & {:keys [cell-update-fn, click-handler, first-selected?]}]
  (doto listview
    (property-value! :items observable-item-list)
    (cond->
      (or cell-update-fn click-handler)
      (listview-cell-factory! {:cell-update-fn cell-update-fn, :click-handler click-handler}))))


(defn get-column-constraint
  [^GridPane grid-pane, ^long index]
  (-> grid-pane .getColumnConstraints (.get index)))


(defn column-constraint-update!
  [^ColumnConstraints column-constraint, {:keys [min-width, max-width, fill-width?]}]
  (cond-> column-constraint
    fill-width? (doto (.setFillWidth true))
    min-width (doto (.setMinWidth min-width))
    max-width (doto (.setMaxWidth max-width))))


(defn column-constraint
  [{:keys [min-width, max-width, fill-width?] :as settings}]
  (column-constraint-update! (ColumnConstraints.), settings))


(defn column-constraint!
  [^GridPane grid-pane, {:keys [min-width, max-width, fill-width?] :as settings}]
  (let [constraint (column-constraint-update! (ColumnConstraints.), settings)]
    (doto grid-pane
      (->
        .getColumnConstraints
        (.add constraint)))))


(defn get-row-constraint
  [^GridPane grid-pane, ^long index]
  (-> grid-pane .getRowConstraints (.get index)))


(defn row-constraint-update!
  [^RowConstraints row-constraint, {:keys [min-height, max-height, fill-height?]}]
  (cond-> row-constraint
    fill-height? (doto (.setFillHeight true))
    min-height (doto (.setMinHeight min-height))
    max-height (doto (.setMaxHeight max-height))))


(defn row-constraint
  [{:keys [min-height, max-height, fill-height?] :as settings}]
  (row-constraint-update! (RowConstraints.), settings))


(defn row-constraint!
  [^GridPane grid-pane, {:keys [min-height, max-height, fill-height?] :as settings}]
  (let [constraint (row-constraint settings)]
    (doto grid-pane
      (->
        .getRowConstraints
        (.add constraint)))))


(defn constraint-update!
  [^GridPane grid-pane, constraint-type, settings]
  (assert (#{:row, :column} constraint-type) "constraint type must be :row or :column")
  (let [[update-fn, constraint-coll] (case constraint-type
                                       :row [row-constraint-update! (.getRowConstraints grid-pane)]
                                       :column [column-constraint-update! (.getColumnConstraints grid-pane)])]
    (doseq [constraint constraint-coll]
      (update-fn constraint, settings))
    grid-pane))



(defn update-row-constraint
  [^RowConstraints row-constraint, min-length, max-length, fill?]
  (doto row-constraint
    (cond-> min-length (.setMinHeight min-length))
    (cond-> max-length (.setMaxHeight max-length))
    (cond-> fill? (.setFillHeight true))))


(defn update-column-constraint
  [^ColumnConstraints column-constraint, min-length, max-length, fill?]
  (doto column-constraint
    (cond-> min-length (.setMinWidth min-length))
    (cond-> max-length (.setMaxWidth max-length))
    (cond-> fill? (.setFillWidth true))))


(defn grid-constraint
  [constraint-type, {:keys [min-length, max-length, fill?]}]
  (assert (#{:row, :column} constraint-type) "constraint type must be :row or :column")
  (case constraint-type
    :row (update-row-constraint (RowConstraints.), min-length, max-length, fill?)
    :column (update-column-constraint (ColumnConstraints.), min-length, max-length, fill?)))


(defn grid-constraint-fixed-length
  [constraint-type, length]
  (grid-constraint constraint-type, {:min-length length, :max-length length}))


(defn add-grid-constraint
  [^GridPane grid-pane, constraint]
  (let [constraint-class (class constraint)]
    (assert (or (= constraint-class RowConstraints) (= constraint-class ColumnConstraints)) "constraint must be an instance of either RowConstraints or ColumnConstraints")
    (cond
      (= constraint-class RowConstraints) (-> grid-pane .getRowConstraints (.add constraint))
      (= constraint-class ColumnConstraints) (-> grid-pane .getColumnConstraints (.add constraint)))
    grid-pane))


(defn remove-grid-constraints
  [^GridPane grid-pane, constraint-type, from, to]
  (assert (#{:row, :column} constraint-type) "constraint type must be :row or :column")
  (case constraint-type
    :row (-> grid-pane .getRowConstraints (.remove from, to))
    :column (-> grid-pane .getColumnConstraints (.remove from, to)))
  grid-pane)


(defn grid-constraints
  [^GridPane grid-pane, constraint-type]
  (assert (#{:row, :column} constraint-type) "constraint type must be :row or :column")
  (case constraint-type
    :row (.getRowConstraints grid-pane)
    :column (.getColumnConstraints grid-pane)))


(defn update-grid-constraint
  [constraint, {:keys [min-length, max-length, fill?]}]
  (let [constraint-class (class constraint)]
    (assert (or (= constraint-class RowConstraints) (= constraint-class ColumnConstraints)) "constraint must be an instance of either RowConstraints or ColumnConstraints")
    (cond
      (= constraint-class RowConstraints) (update-row-constraint constraint, min-length, max-length, fill?)
      (= constraint-class ColumnConstraints) (update-column-constraint constraint, min-length, max-length, fill?))))


(defn update-all-grid-constraints
  [^GridPane grid-pane, constraint-type, {:keys [min-length, max-length, fill?] :as settings}]
  (assert (#{:row, :column} constraint-type) "constraint type must be :row or :column")
  (u/for-each!
    (fn [constraint]
      (update-grid-constraint constraint, settings))
    (grid-constraints grid-pane constraint-type))
  grid-pane)



(defn update-grid-constraint-fixed-length
  [constraint, length]
  (update-grid-constraint constraint, {:min-length length, :max-length length}))


(defn update-all-grid-constraints-fixed-length
  [^GridPane grid-pane, constraint-type, length]
  (assert (#{:row, :column} constraint-type) "constraint type must be :row or :column")
  (u/for-each!
    (fn [constraint]
      (update-grid-constraint-fixed-length constraint, length))
    (grid-constraints grid-pane constraint-type))
  grid-pane)





(defn canvas-size!
  [^Canvas canvas, ^double width, ^double height]
  (doto canvas
    (.setWidth width)
    (.setHeight height)))



(defn toggle-group
  ^ToggleGroup [& toggles]
  (let [group (ToggleGroup.)]
    (doseq [^Toggle tgl toggles]
      (.setToggleGroup tgl, group))
    group))


(defn to-front
  [node-or-stage]
  (cond
    (instance? Node node-or-stage) (doto ^Node node-or-stage .toFront)
    (instance? Stage node-or-stage) (doto ^Stage node-or-stage .toFront)
    :else node-or-stage))


(defn to-back
  [node-or-stage]
  (cond
    (instance? Node node-or-stage) (doto ^Node node-or-stage .toBack)
    (instance? Stage node-or-stage) (doto ^Stage node-or-stage .toBack)
    :else node-or-stage))



(defn table-model-row
  [column-keys, data-ref, data-prefix, position]
  (->> (persistent!
         (reduce
           (fn [row-map, column]
             (let [value-path (-> (vec data-prefix)
                                (conj position)
                                ((if (sequential? column) into conj) column))]
               (assoc! row-map column (property-in-ref data-ref, value-path))))
           (transient {})
           column-keys))
    (hash-map :data-position position, :column-properties)))


(defn notify-row-listeners
  [^ObservableList observable-list, old-state, new-state]
  (doseq [observable-map observable-list]
    (reduce-kv
      (fn [_, _, observable]
        (notify-listeners observable, old-state, new-state)
        nil)
      nil
      (:column-properties observable-map))))


(defn update-table-model
  [^WeakReference observable-list-weak-ref, create-row, data-prefix, key, ref, old-state, new-state]
  (if-let [^ObservableList observable-list (.get observable-list-weak-ref)]
    (let [new-row-count (count (get-in new-state data-prefix)),
          old-row-count (count (get-in old-state data-prefix)),
          delta (- new-row-count old-row-count)]
      (when-not (zero? delta)
        (if (pos? delta)
          ; add rows
          (u/iteration-indexed delta,
            (fn [i, ^List observable-list]
              (doto observable-list
                (.add (create-row (+ old-row-count i)))))
            observable-list)
          ; remove rows with a previous data-position larger than or equal to new-row-count
          (.remove observable-list new-row-count, old-row-count)))
      (notify-row-listeners observable-list, old-state, new-state))
    ; observable list has been gc'ed -> no watch needed
    (remove-watch ref, key)))


(defn table-model
  ^ObservableList [column-keys, data-ref, data-prefix]
  (let [data-prefix (when data-prefix (if (sequential? data-prefix) data-prefix [data-prefix]))
        create-row (partial table-model-row column-keys, data-ref, data-prefix),
        row-count (-> data-ref deref (get-in data-prefix) count),
        observable-list (FXCollections/observableArrayList ^List (mapv create-row (range row-count)))]
    ; use a weak reference to allow garbage collection of observable list
    (add-watch data-ref (System/identityHashCode observable-list) (partial update-table-model (WeakReference. observable-list), create-row, data-prefix))
    observable-list))


(defn column-cell-value-factory!
  [^TableColumn column, column-key, value-fn]
  (doto column
    (.setCellValueFactory
      (reify Callback
        (call [this, cdf]
          (let [prop (get-in (.getValue ^TableColumn$CellDataFeatures cdf) [:column-properties, column-key])]
            (if value-fn
              (functional-property value-fn prop)
              prop)))))))


(defn column-cell-factory!
  [^TableColumn column, table-model, cell-creation-fn]
  (doto column
    (.setCellFactory
      (if (instance? Callback cell-creation-fn)
        cell-creation-fn
        (reify Callback
          (call [this, column]
            (cell-creation-fn)))))))


(defn predicate?
  [x]
  (instance? Predicate x))


(defn predicate
  [f]
  (cond
    (ifn? f)
    (reify Predicate
      (test [_, value]
        (f value)))

    (predicate? f)
    f

    :else
    (u/illegal-argument "Unsupported input of type \"%s\" is neither a function nor a predicate." (class f))))





(defn filter-predicate
  [filter-text]
  (predicate
    (fn [{:keys [column-properties] :as row-map}]
      (boolean
        (reduce-kv
          (fn [_, _, column-prop]
            (when-let [value (some->> column-prop value str/lower-case)]
              (when (.contains (str value) filter-text)
                (reduced true))))
          nil
          column-properties)))))


(defn and-predicate
  [^Predicate a, ^Predicate b]
  (.and a b))


(defn setup-filtering
  [^ObservableList table-model, column-keys, filter-text-property]
  (let [filtered-model (FilteredList. table-model, (predicate (constantly true)))]
    (bind (property filtered-model, :predicate)
      (functional-property
        (fn [filter-text]
          (let [filter-text-vec (->> (str/split filter-text #"\s")
                                  (mapv (comp str/lower-case str/trim)))]
            (reduce
              (fn [combined-pred, filter-text]
                (and-predicate combined-pred, (filter-predicate filter-text)))
              (predicate (constantly true))
              filter-text-vec)))
        filter-text-property))
    filtered-model))


(defn setup-sorting
  [^ObservableList table-model, ^TableView table-view]
  (let [sorted-model (SortedList. table-model)]
    ; set Clojure compare as default comparator
    (doseq [^TableColumn column (.getColumns table-view)]
      (property-value! column, :comparator, u/universal-compare))
    (bind (property sorted-model, :comparator) (property table-view, :comparator))
    sorted-model))


(defn setup-table-view!
  ^TableView [^TableView table-view, column-key-paths, data-ref, & {:keys [placeholder, column-cell-factories, column-cell-value-fns, filter-text-property, sort?, data-prefix]}]
  (let [model (cond-> (table-model column-key-paths, data-ref, data-prefix)
                filter-text-property (setup-filtering column-key-paths, filter-text-property)
                sort? (setup-sorting table-view))]
    (u/for-each-indexed!
      (fn [pos, ^TableColumn column]
        (let [column-path (nth column-key-paths pos)
              ; use the single specified key or last key in path
              column-key (if (sequential? column-path) (last column-path) column-path)]
          (column-cell-value-factory! column, column-path, (get column-cell-value-fns column-key))))
      (.getColumns table-view))
    (when column-cell-factories
      (u/for-each-indexed!
        (fn [pos, column]
          (let [column-path (nth column-key-paths pos)
                ; use the single specified key or last key in path
                column-key (if (sequential? column-path) (last column-path) column-path)]
            (when-let [cell-factory (get column-cell-factories column-key)]
              (column-cell-factory! column, model, cell-factory))))
        (.getColumns table-view)))
    (doto table-view
      (.setItems model)
      (cond-> placeholder (.setPlaceholder (Label. placeholder))))))


(defn sort-by-column!
  [^TableView table-view, ^long column-index]
  (let [column (.get (.getColumns table-view) column-index)]
    (doto (.getSortOrder table-view)
      (.clear)
      (.add column)))
  table-view)


(defn column-comparator!
  [^TableView table-view, ^long column-index, comparator]
  (let [^TableColumn column (.get (.getColumns table-view) column-index)]
    (.setComparator column, comparator))
  table-view)


(defn column-captions!
  [^TableView table-view, column-captions]
  (doseq [[^TableColumn column caption] (mapv vector (.getColumns table-view) column-captions)]
    (.setText column, caption))
  table-view)


(defn tableview-comparator!
  [^TableView table-view, comparator]
  (doto table-view
    (property-value! :comparator, comparator)))


(defn selected-rows-property
  [^TableView table-view]
  (let [selection-model (.getSelectionModel table-view)]
    (functional-property
      (fn [rows]
        (mapv
          (fn [{:keys [column-properties]}]
            (persistent!
              (reduce-kv
                (fn [row-map, k, prop]
                  (assoc! row-map (cond-> k (sequential? k) last) (value prop)))
                (transient {})
                column-properties)))
          rows))
      (.getSelectedItems selection-model))))


(defn selected-row
  [^TableView table-view]
  (-> table-view selected-rows-property value first))


(defn table-columns!
  ^TableView [^TableView table-view, column-spec-list]
  (let [column-list (.getColumns table-view)]
    (clear-list column-list)
    (doseq [{:keys [text, style]} column-spec-list]
      (add-to-list column-list,
        (doto (TableColumn. text)
          (cond-> style (.setStyle style))))))
  table-view)


(defn radiobutton-cell
  []
  (RadioButtonTableCell.))


(defn checkbox-cell
  []
  (CheckBoxTableCell.))


(defn combobox-selection-cell
  [value-list-key, selected-index-key, & {:keys [selection-prompt]}]
  (ComboBoxSelectionTableCell. value-list-key, selected-index-key, selection-prompt))


(defn cell-in-selected-row
  "Returns the value of the cell in the given column of the currently selected row."
  [^TableView table-view, column-key]
  (when-let [^ObservableValue observable (some-> table-view .getSelectionModel .getSelectedItem (get-in [:column-properties, column-key]))]
    (.getValue observable)))


(defn selected-row-data-index
  "Returns the position of the data of the selected row in the data model."
  [^TableView table-view]
  (some-> table-view .getSelectionModel .getSelectedItem :data-position))


(defn selected-row-data-indices
  "Returns the positions of the data of the selected rows in the data model."
  [^TableView table-view]
  (when-let [^MultipleSelectionModel selection-model (.getSelectionModel table-view)]
    (mapv
      :data-position
      (.getSelectedItems selection-model))))


(defn value->keyword-property
  "Creates a functional property containing the specified keyword of the corresponding value or nil."
  [keyword->value-map, prop]
  (functional-property
    (fn [current-value]
      (reduce-kv
        (fn [_, kw, value]
          (when (= current-value value)
            (reduced kw)))
        nil
        keyword->value-map))
    prop))


(defn selected-toggle-property
  "Creates a functional property containing the specified keyword of the selected radiobutton with respect to the set toggle group."
  [keyword->radiobutton-map]
  (let [toggle-groups (->> keyword->radiobutton-map
                        vals
                        (mapv #(.getToggleGroup ^Toggle %))
                        set)]
    (case (count toggle-groups)
      1 (let [^ToggleGroup toggle-group (first toggle-groups)]
          (value->keyword-property keyword->radiobutton-map, (property toggle-group, :selected-toggle)))
      0 (u/illegal-argument "The specified radiobuttons have no toggle group!")
      (u/illegal-argument "Not all of the specified radiobuttons are assigned to the same toggle group!"))))


(defn combobox-bidi
  [^ComboBox combobox, values-prop, selected-value-prop, value->item-map]
  (let [item->value-map (u/inverse-map value->item-map)
        selection-model (.getSelectionModel combobox)]
    ; item change -> value change
    (change-listener! (.selectedItemProperty selection-model),
      (fn [_, old-item, new-item]
        (when-not (= old-item new-item)
          (value! selected-value-prop (get item->value-map new-item)))))
    ; value change -> item change
    (change-listener! selected-value-prop,
      (fn [_, old-value, new-value]
        (when-not (= old-value new-value)
          (.select selection-model (get value->item-map new-value)))))
    ; set items
    (.setItems combobox
      (property->observable-list
        (functional-property
          (fn [values]
            (mapv #(get value->item-map %) values))
          values-prop))))
  combobox)


(defn rounded-long
  [^long n]
  (fn [x]
    (when (number? x)
      (-> (Math/floor (/ (double x) n)) (* n) long))))


(defn rounded-double
  [^double n]
  (fn [x]
    (when (number? x)
      (-> (Math/floor (/ (double x) n)) (* n)))))


(defn ensure-property
  [x]
  (cond-> x (not (property? x)) (object-property)))


(defn spinner-bidi
  [^Spinner spinner, type, min, max, step, value-prop]
  (let [round (case type
                :double (rounded-double step)
                :long (rounded-long step))
        min-prop (ensure-property min)
        max-prop (ensure-property max)
        ^SpinnerValueFactory
        value-factory (case type
                        :double (doto (SpinnerValueFactory$DoubleSpinnerValueFactory.
                                        (value min-prop), (value max-prop), (round (value value-prop)), step)
                                  (-> .minProperty (bind min-prop))
                                  (-> .maxProperty (bind max-prop)))
                        :long (doto (SpinnerValueFactory$IntegerSpinnerValueFactory.
                                      (value min-prop), (value max-prop), (round (value value-prop)), step)
                                (-> .minProperty (bind min-prop))
                                (-> .maxProperty (bind max-prop))))
        editor (.getEditor spinner),
        converter (.getConverter value-factory)]

    ; set value factory
    (.setValueFactory spinner value-factory)
    ; round to step and update editor
    (listen-to
      (fn [value]
        (property-value! editor, :text (.toString converter, value)))
      (property value-factory, :value))
    ; sync edit with spinner value (on focus lost) via formatter (cf. https://stackoverflow.com/a/32349847)
    (let [formatter (TextFormatter. converter, (.getValue value-factory))]
      (.setTextFormatter editor formatter)
      (bind-bidirectional (property value-factory, :value), (property formatter, :value)))
    ; bind spinner value to given value property
    (bind-bidirectional value-prop, (property value-factory, :value))
    spinner))


(defn close-all-windows
  "Close all JavaFX windows."
  []
  (run-now
    (doseq [^Window window (Window/getWindows)]
      (.hide window))))


(defn double-click?
  "Does the mouse event represent a double click?"
  [^MouseEvent e]
  (and
    (= (.getClickCount e) 2)
    (= (.getButton e) MouseButton/PRIMARY)))


(defn select-all-text
  [^TextInputControl text-input-control]
  (doto text-input-control
    (.selectAll)))


(defn request-focus
  [^Node node]
  (doto node
    (.requestFocus)))


(defn select-entity-from-table
  [entity+info-coll, {:keys [title, description, entity-name, info-name, select-first?, inspect]}]
  (run-now
    (let [control (create-control "clj_jfx/TableSelectionDialog.fxml"),
          control-node (control-node control),
          {:keys [entity-tableview,
                  ^Label
                  description-label,
                  select-button,
                  cancel-button,
                  inspect-button] :as children} (control-children control)
          window (modal-window (or title "Select"), control-node),
          result-data (atom nil),
          table-data (atom (vec entity+info-coll)),
          selected-entity-prop (functional-property
                                 (comp :entity first)
                                 (selected-rows-property entity-tableview))]

      (property-value! description-label, :text, (or description "Select a table entry"))

      (setup-table-view! entity-tableview, [:entity, :info], table-data, :sort? true)
      (column-captions! entity-tableview, [(or entity-name "entity"), (or info-name "info")])

      (when select-first?
        (select-first! entity-tableview))

      (if inspect
        (handle-event! inspect-button, :action
          (fn [_]
            (inspect (value selected-entity-prop))))
        (remove-children (parent inspect-button), inspect-button))

      (handle-event! select-button, :action
        (fn [_]
          (reset! result-data (value selected-entity-prop))
          (close window)))

      (handle-event! cancel-button, :action
        (fn [_]
          (close window)))

      (show-and-wait window)
      (deref result-data))))


(let [host-services (.getHostServices (proxy [Application] []))]
  (defn show-document
    [url]
    (.showDocument host-services url)))


(defn hyperlink
  ([url]
   (hyperlink url, url))
  ([text, url]
   (doto (Hyperlink. text)
     (handle-event! :action (fn [_] (show-document url))))))


(defn vbox
  [& children]
  (doto (VBox.)
    (add-children children)))


(defn hbox
  [& children]
  (doto (HBox.)
    (add-children children)))



(defn copy-selected-table-rows-to-clipboard
  [^TableView tableview]
  (let [selected-rows (vec (sort (.getSelectedIndices (.getSelectionModel tableview)))),
        row-data (->> (.getColumns tableview)
                   (mapv (fn [^TableColumn col]
                           (list*
                             ; column name
                             (.getText col)
                             ; column values
                             (mapv
                               (fn [row-index]
                                 (.getCellData col (int row-index)))
                               selected-rows))))
                   (apply mapv vector)),
        content (doto (ClipboardContent.)
                  (.putString (->> row-data (mapv #(str/join "\t" %)) (str/join "\n"))))]
    (.setContent (Clipboard/getSystemClipboard) content)))


(defn enable-copy-to-clipboard!
  [^TableView tableview]
  (doto tableview
    (handle-event! :key-pressed,
      (fn [^KeyEvent e]
        (when (and (.isShortcutDown e) (= (.getCode e) KeyCode/C))
          (copy-selected-table-rows-to-clipboard tableview))))))



(defn show-custom-alert
  [{:keys [alert-type, title, header, content-text, content, expandable-content]}]
  (run-now
    (let [alert (doto (Alert. ({:information Alert$AlertType/INFORMATION,
                                :error Alert$AlertType/ERROR,
                                :warning Alert$AlertType/WARNING,
                                :confirmation Alert$AlertType/CONFIRMATION} alert-type))
                  (.setTitle (or title (str/capitalize (name alert-type))))
                  (.setHeaderText header)
                  (cond-> content-text (.setContentText content-text)))
          dialog-pane (.getDialogPane alert)]
      (when content
        (.setContent dialog-pane content))
      (when expandable-content
        (.setExpandableContent dialog-pane expandable-content)
        (listen-to
          (fn [_]
            (run-later
              (.requestLayout dialog-pane)
              (-> dialog-pane .getScene .getWindow .sizeToScene)))
          (property dialog-pane, :expanded)))
      (.showAndWait alert))))


(defn show-errors-detailed
  [title, header, error-messages]
  (show-custom-alert
    {:alert-type :error
     :title title
     :content-text header
     :expandable-content (doto (ListView. (observable-array-list error-messages))
                           (min-height! 50)
                           (pref-height! (+ 2 (* 28 (min (count error-messages) 5))))
                           (max-height! 200))}))


(defn and-properties
  [& properties]
  (apply functional-property
    (fn [& values]
      (reduce
        (fn [result, v]
          (if v
            result
            (reduced false)))
        true
        values))
    properties))


(defn add-style-class
  [^Styleable styleable, style-class]
  (doto styleable
    (-> .getStyleClass (.add style-class))))