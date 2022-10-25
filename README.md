git clone https://github.com/bloxbean/cardano-client-tx-evaluator.git
git submodule update --init --recursive

. script/build-<os>-<arch>.sh

./gradlew build
