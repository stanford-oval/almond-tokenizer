# Almond Tokenizer

This repository contains the Almond preprocessor and tokenizer.
It is derived from SEMPRE, a semantic parser developed by prof. Percy Liang and his students,
but it has diverged significantly.

## Installation

Download dependencies:

    ./pull-dependencies.sh

Build the core:

    JAVA_HOME=<path to java> ant

`JAVA_HOME` should be set to the path to your Java installation, eg. `/usr/lib/jvm/openjdk-1.8.0`.
Java 1.8 is required. A working C compiler must also be installed.

## Run the tokenizer service

    ./run.sh

The tokenizer listens on port 8888 by default.

An example systemd unit service is provided as `almond-tokenizer.service`.
The service supports socket activation as well.

## Using the Location lexicon

If you plan to include location queries (as default in Almond), you must obtain a MapQuest
API key, and set it as the `MAPQUEST_KEY` environment variable.
