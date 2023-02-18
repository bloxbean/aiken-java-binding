tag=v0.0.7

mkdir -p native/linux-x86-64
mkdir -p native/darwin-aarch64
mkdir -p native/darwin-x86-64
mkdir -p native/win32-x86-64

echo $1

echo "Downloading linux-x86-64"
wget https://github.com/bloxbean/aiken-jna-wrapper/releases/download/$tag/linux-x86-64_libaiken_jni_wrapper.so -O native/linux-x86-64/libaiken_jna_wrapper.so

echo "Downloading darwin-aarch64"
wget https://github.com/bloxbean/aiken-jna-wrapper/releases/download/$tag/darwin-aarch64_libaiken_jni_wrapper.dylib  -O native/darwin-aarch64/libaiken_jna_wrapper.dylib

echo "Downloading darwin-x86-64"
wget https://github.com/bloxbean/aiken-jna-wrapper/releases/download/$tag/darwin-x86-64_libaiken_jni_wrapper.dylib -O  native/darwin-x86-64/libaiken_jna_wrapper.dylib

echo "Downloading win32-x86-64"
wget https://github.com/bloxbean/aiken-jna-wrapper/releases/download/$tag/windows-x86-64_aiken_jni_wrapper.dll -O  native/win32-x86-64/aiken_jna_wrapper.dll
