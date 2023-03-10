(ns nextjournal.markdown.transform-test
  (:require #?(:clj [clojure.test :refer :all]
               :cljs [cljs.test :refer (deftest testing is)])
            [matcher-combinators.test]
            [nextjournal.markdown :as md]
            [nextjournal.markdown.transform :as md.transform]))

(def test-text "# Ahoi
this is *just* a __strong__ ~~text~~ with a $\\phi$ and a #hashtag

this is an ![inline-image](/some/src) and a [_link_](/foo/bar)

par with a sidenote at the end[^sidenote] and another[^sn2] somewhere

```clojure
(+ 1 2)
```

$$\\int_a^b\\phi(t)dt$$

* _this_

  * sub1
  * sub2 some bla
    bla bla

* is not

  2. nsub1
  3. nsub2
  4. nsub3

* thight
  - [ ] undone
  - [x] done

* > and
  > a nice
  > quote

![block *image*](/some/src)

> so what
> is this

1. one
2. two

---

another

| _col1_        | col2                    |
|:-------------:|:------------------------|
| whatthasdasfd | hell                    |
| this is       | insane as as as as as f |

> * one
> * two

end

[^sidenote]: Here a __description__
[^sn2]: And some _other_
")

(deftest ->md
  (let [doc (md/parse test-text)]
    (is (= doc
           (-> doc
               md.transform/->md
               md/parse)))))
