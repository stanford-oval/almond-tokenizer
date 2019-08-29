FROM ubuntu:18.04

MAINTAINER Thingpedia Admins <thingpedia-admins@lists.stanford.edu>

# install required dependencies
RUN apt-get -y update
RUN apt-get install -y wget git ant

# add user almond-tokenizer
RUN useradd -ms /bin/bash -r almond-tokenizer
USER almond-tokenizer

# downlod source and install packages
WORKDIR /home/almond-tokenizer
COPY . ./almond-tokenizer
WORKDIR /home/almond-tokenizer/almond-tokenizer
RUN ./pull-dependencies.sh 
RUN ant

# entry point
ENV PORT 8888
ENV LANGUAGES en zh-hans zh-hant
CMD ["/bin/bash", "run.sh"]
