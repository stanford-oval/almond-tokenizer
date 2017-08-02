#!/bin/bash

#
# Extracts and preprocesses all datasets for Seq2Seq training and evaluation
#
# Produces the following files:
# - base-author.tsv
# - paraphrasing-train+dev.tsv
# - generated.tsv
# - train.tsv
# - dev.tsv
# - paraphrasing-test-prim.tsv
# - paraphrasing-test-compound.tsv
# - scenario-prim.tsv
# - scenario-compound.tsv
# - cheatsheet-prim.tsv
# - cheatsheet-compound.tsv
#
#
# Usage:
#
# WORKDIR=... ./scripts/extract-seq2seq-all.sh ${DATABASE_PW}

DATABASE_PW=${DATABASE_PW:-$1}
shift
set -x
set -e

SEMPREDIR=`dirname $0`/..
SEMPREDIR=`realpath ${SEMPREDIR}`
WORKDIR=${WORKDIR:-.}
GLOVE=${GLOVE:-$WORKDIR/glove.txt}
EXTRA_ARGS="-ThingpediaDatabase.dbPw ${DATABASE_PW} $@"

BASEAUTHOR_TRAIN="thingpedia online"
PARA_TRAINDEV="turking-prim0 turking-prim1 turking-prim2 turking-prim3 turking3-prim1 turking-compound0 turking-compound1 turking-compound2 turking-compound3 turking-compound4 turking2-compound0 turking2-compound1 turking2-compound2 turking2-compound3 turking2-compound4 turking2-compound5 turking2-compound6 turking2-prim0 turking2-prim1 turking2-prim2 turking3-compound0 turking3-compound1 turking3-compound2 turking3-compound3 turking3-compound4 turking3-compound5 turking3-compound6"
GENERATED="generated-highvariance"
PARA_TEST_PRIM="test-prim0 test-prim1 test-prim2 test-prim3"
PARA_TEST_COMPOUND="test-compound0 test-compound1 test-compound2 test-compound3 test-compound4 test3-compound0 test3-compound1 test3-compound2 test3-compound3 test3-compound4 test3-compound5 test3-compound6"

SCENARIO="test-scenarios test-scenarios2"
CHEATSHEET="test-cheatsheet test-cheatsheet2"

do_one() {
${SEMPREDIR}/scripts/run-extract-seq2seq.sh -ExtractSeq2Seq.types $1 -ExtractSeq2Seq.output ${WORKDIR}/$2.tsv ${EXTRA_ARGS}
}

# extract the datasets
do_one "$BASEAUTHOR_TRAIN" base-author
do_one "$PARA_TRAINDEV" paraphrasing-train+dev
do_one "$GENERATED" generated
do_one "$PARA_TEST_PRIM" paraphrasing-test-prim
do_one "$PARA_TEST_COMPOUND" paraphrasing-test-compound
do_one "$SCENARIO" scenario
do_one "$CHEATSHEET" cheatsheet
do_one "generated-cheatsheet" generated-cheatsheet

# split dev and train
N=$(cat ${WORKDIR}/paraphrasing-train+dev.tsv | wc -l)
DEV_N=$((N/10))
TRAIN_N=$((N-DEV_N))
sort -R -t" " -k2 ${WORKDIR}/paraphrasing-train+dev.tsv > ${WORKDIR}/tmp
head -n${TRAIN_N} ${WORKDIR}/tmp > ${WORKDIR}/paraphrasing-train.tsv
tail -n${DEV_N} ${WORKDIR}/tmp > ${WORKDIR}/dev.tsv
cat ${WORKDIR}/paraphrasing-train.tsv ${WORKDIR}/base-author.tsv ${WORKDIR}/generated.tsv ${WORKDIR}/generated-cheatsheet.tsv > ${WORKDIR}/train.tsv
rm ${WORKDIR}/tmp

# split scenario and cheatsheet prim and compound
grep    "	rule" ${WORKDIR}/scenario.tsv > ${WORKDIR}/scenario-compound.tsv
grep -v "	rule" ${WORKDIR}/scenario.tsv > ${WORKDIR}/scenario-prim.tsv
grep    "	rule" ${WORKDIR}/cheatsheet.tsv > ${WORKDIR}/cheatsheet-compound.tsv
grep -v "	rule" ${WORKDIR}/cheatsheet.tsv > ${WORKDIR}/cheatsheet-prim.tsv
