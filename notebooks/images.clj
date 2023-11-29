;; # 🖼️ Block Level Images
(ns images
  {:nextjournal.clerk/visibility {:code :hide :result :show}}
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.markdown :as md]
            [nextjournal.markdown.transform :as md.transform]))

;; Unlike [commonmark](https://spec.commonmark.org/0.30/#example-571),
;; nextjournal.markdown distinguishes between inline images and _block images_: image syntax which span a whole
;; line of text produces a direct child of the document and is not wrapped in a paragraph note. Take the following text

^{::clerk/viewer {:var-from-def? true
                  :transform-fn #(clerk/html [:pre @(::clerk/var-from-def (:nextjournal/value %))])}}
(def text-with-images
  "This example shows how we're parsing images, the following is a _block image_

![block level image](https://images.freeimages.com/images/large-previews/773/koldalen-4-1384902.jpg)

while this is an inline ![inline](https://github.com/nextjournal/clerk/actions/workflows/main.yml/badge.svg) image.
")

;; This is parsed as

(clerk/code
 (dissoc (md/parse text-with-images)
         :toc :footnotes))

;; This allows for a different rendering of images, for instance we might want to render block images with a caption:

^{::clerk/visibility {:code :show} :nextjournal.clerk/viewer 'nextjournal.clerk.viewer/html-viewer}
(md.transform/->hiccup
 (assoc md.transform/default-hiccup-renderers
        :image (fn [{:as _ctx ::md.transform/keys [parent]} {:as node :keys [attrs]}]
                 (if (= :doc (:type parent))
                   [:figure.image
                    [:img (assoc attrs :alt (md.transform/->text node))]
                    [:figcaption.text-center.mt-1 (md.transform/->text node)]]
                   [:img.inline (assoc attrs :alt (md.transform/->text node))])))
 (md/parse text-with-images))
