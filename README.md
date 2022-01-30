# Ice Breaker: Sentence Chain

## Overview

The goal of this ice breaker is to generate a sentence.  Each person in the group will say one word of the sentence.  Expect tons of creativity from that!

**NOTE:** Thanks to [Brian Gorman](https://briangorman.io/) for writing [Lets build a Clojurescript chatroom](https://briangorman.io/posts/chatroom-brian-gorman/) which I used to learn about how to use Websockets and manage Clojure and ClojureScript apps on the same project.

## Setup and Development

To get an interactive development environment run:

    lein figwheel

and open your browser at [localhost:3449](http://localhost:3449/).  This will auto compile and send all changes to the browser without the need to reload. After the compilation process is complete, you will get a Browser Connected REPL. An easy way to try it is:

    (js/alert "Am I connected?")

and you should see an alert in the browser window.

To clean all compiled files:

    lein clean

To create a production build run:

    lein do clean, cljsbuild once min

And open your browser in `resources/public/index.html`. You will not get live reloading, nor a REPL.

## Development and Run

To get a standalone verison of the application run:

    lein uberjar

To execute the webserver run:

    java -cp ice-breaker-sentence-chain-standalone.jar clojure.main -m ice-breaker-sentence-chain.server.main 3449
