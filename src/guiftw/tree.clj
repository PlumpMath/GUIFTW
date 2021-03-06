(ns guiftw.tree
  "Core of GUI tree stucture parsing. The parse-gui macro is heart of it.

  Syntax for GUI structure:

  [class1 [prop1 value1, prop2 value2, ...]
   [class2 [prop3 value3, ...] ...]
   [class3 [prop4 value4, ...] ...]
   ...]

  Each node begins with class name followed by private style sheet and
  any number of children. Syntax for style sheets is described in
  guiftw.styles doc. Uses extra properties:

  *id -- unique identifier of the object,
  *groups -- seq of groups identifiers where this object belongs.

  These properties are ignored when used in stylesheets created by
  guiftw.styles/stylesheet macro.

  Various functions return so-called GUI state wich is a map wrapped
  in atom. This map contains predefined keys:

  :ids -- a map with objects indentified by their :*id property given
          in style sheet.
  :groups -- a map with lists of objects grouped by :*group property
             given in the style sheet,
  :root -- top-level object in GUI tree (usually window).

  User can add any custom key/values at will. They will be preserved."
  (:require (guiftw [styles :as styles]
		    [props :as props])))

(defmacro constructor
  "Returns multi-variant function that reflects all constructors for
  given class."
  [class]
  (if-not (resolve class)
    (throw (ClassNotFoundException. (name class))))
  `(fn ~@(map (fn [c]
		(let [syms (repeatedly c gensym)]
		  (list (vec syms) (cons (symbol (str (.getName class) "."))
					 syms))))
	      (distinct (map #(-> % .getParameterTypes count)
			     (.getConstructors (resolve class)))))))

(defn merge-guis
  "Merges two GUI states (maps). Keys :ids, :groups and :root are
  treated differently: :ids are merged, all values inside :groups are
  concatenated and :root is preserved from old map.

  All other keys are merged like in merge function."
  [old new]
  (apply merge
	 {:ids (merge (:ids old) (:ids new))
	  :groups (merge-with concat (:groups old) (:groups new))
	  :root (or (:root old) (:root new))}
	 (map #(dissoc % :ids :groups :root) [old new]))) ;; merge rest of map traditionally

(defn gui-creator-args-dispatch
  "Helper function for gui-creator. Dispatches args to map with keys:
  :stylesheets -- all stylesheets concatenated
  :gui -- state
  :parent -- component where widgets will be added."
  [args]
  (apply merge-with concat
         (map (fn [x] {(cond (nil? x) nil
                            (sequential? x) :stylesheets
                            (instance? clojure.lang.IDeref x) :gui
                            (instance? guiftw.props.Property x) :parent-style
                            :else :parent)
                      x})
              args)))

(defn gui-creator
  "Logic behind creating GUI. It's what parse-gui will return and it's
  not intended to be used outside of it.

  Instantiator have to be 3-arg fn that will construct object using
  constructor and add it to a parent using evetually properties of an
  object (especially *cons -- constructor parameters -- and *lay --
  layout parameters)."
  [instantiator constructor style children]
  (fn [& args]
    (let [{:keys [stylesheets gui parent parent-style]} (gui-creator-args-dispatch args)
          gui (or gui (atom {}))
	  specials (-> style props/get-value :specials)
	  final-style (if-let [reduced (styles/reduce-stylesheet
					(cons (:*id specials) (:*groups specials))
					stylesheets)]
			(styles/cascade reduced style)
			style)
	  parent (or parent (:root @gui))
	  obj (instantiator constructor final-style parent parent-style)]
      (props/set-on final-style gui obj)
      (let [id (if-let [id (:*id specials)]
		 {:ids {id obj}})
	    groups (if-let [groups (:*groups specials)]
		     {:groups (zipmap groups (repeat [obj]))})
	    root {:root obj}
	    gui-news (merge id groups root)]
	(swap! gui merge-guis gui-news)
	(dorun (map #(% gui obj final-style stylesheets)
                    (flatten children))))
      gui)))

(defmacro parse-gui
  "Parses GUI tree (struct) at compile time. Parsing is as abstract as
  possible, given instantiator function is concrete implementation of
  creating object and adding it as a child to it's parent.

  Instantiator function takes three arguments: a constructor
  function (generated multi-variant fn that represents all possible
  constructors for class at in this node), parent object (nil is
  possible) and style for object that will be created.

  Returns a function that takes zero or more arguments: gui state,
  parent object (place where widgets will be added) and any amount of
  style sheets that will be applied to created objects. All parameters
  are optional. Created function will return modified gui state or
  newly created if gui arg is nil. If you pass a map wrapped in an
  atom with some custom keys (:ids, :groups and :root are reserved)
  they will be preserved so you can put there your custom application
  state.

  Use any of concrete implementations like guiftw.swing/swing or
  guiftw.swt/swt instead of this."
  [instantiator struct]
  (let [class (first struct)
        constructor `(constructor ~class)
	has-props (styles/style-spec? (second struct))
	props `(styles/style ~(concat (if has-props (concat (second struct)))
                                      [:*class class]))
	children (if has-props
		   (rest (rest struct))
		   (rest struct))
	children-guis (for [x children] (if (vector? x)
                                          `(parse-gui ~instantiator ~x)
                                          x))]
    `(gui-creator ~instantiator ~constructor ~props (list ~@children-guis))))
