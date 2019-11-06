FROM ubuntu:18.04

MAINTAINER Thingpedia Admins <thingpedia-admins@lists.stanford.edu>

# copy source
RUN useradd -ms /bin/bash -r almond-tokenizer
WORKDIR /home/almond-tokenizer
COPY . ./almond-tokenizer
RUN chown -R root:root ./almond-tokenizer
WORKDIR /home/almond-tokenizer/almond-tokenizer

# install required dependencies and build
RUN apt-get -y update && \
 apt-get install -y wget git ant openjdk-11-jre-headless && \
 ./pull-dependencies.sh && \
 ant && \
 apt-get purge -y ant && \
 apt-get autoremove -y && \
 rm -fr /var/cache

# entry point
ENV PORT 8888
ENV LANGUAGES en zh-hans zh-hant it
ENV LANG C.UTF-8
USER almond-tokenizer
CMD ["/bin/bash", "run.sh"]
