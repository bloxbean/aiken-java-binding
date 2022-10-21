export SRC_LIB_FILE=libaiken-jna-wrapper.dylib
export TARGET_LIB_FILE=darwin-aarch64_libaiken-jna-wrapper.dylib
export NATIVE_FOLDER=darwin-aarch64

cd rust
cargo build --all --release --target aarch64-apple-darwin
cp target/aarch64-apple-darwin/release/$SRC_LIB_FILE target/aarch64-apple-darwin/release/$TARGET_LIB_FILE
cd ..

mkdir -p native/$NATIVE_FOLDER
cp rust/target/aarch64-apple-darwin/release/$SRC_LIB_FILE native/$NATIVE_FOLDER

ls native/$NATIVE_FOLDER && pwd
