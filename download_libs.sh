#!/bin/bash

# Downloads the prebuilt aiken-jna-wrapper native libraries for each supported
# platform from the matching GitHub release into native/<platform>/. The native/
# tree is packaged as resources, and JNA loads the right library per platform at
# runtime, so adding a platform here is all that's needed on the Java side.
#
# Targets aiken-jna-wrapper v0.7.1 — the patch release that ships the
# maintenance/cleanup work and the new aarch64-unknown-linux-gnu target
# (bloxbean/aiken-jna-wrapper#33). The exported FFI functions are unchanged, so
# no Java interface changes are required.
tag=v0.7.1

mkdir -p native/linux-x86-64
mkdir -p native/linux-aarch64
mkdir -p native/darwin-aarch64
mkdir -p native/darwin-x86-64
mkdir -p native/win32-x86-64
mkdir -p native/win32-aarch64

echo $tag > native/version

echo "Downloading linux-x86-64"
wget https://github.com/bloxbean/aiken-jna-wrapper/releases/download/$tag/linux-x86-64_libaiken_jna_wrapper.so -O native/linux-x86-64/libaiken_jna_wrapper.so

echo "Downloading linux-aarch64"
wget https://github.com/bloxbean/aiken-jna-wrapper/releases/download/$tag/linux-aarch64_libaiken_jna_wrapper.so -O native/linux-aarch64/libaiken_jna_wrapper.so

echo "Downloading darwin-aarch64"
wget https://github.com/bloxbean/aiken-jna-wrapper/releases/download/$tag/darwin-aarch64_libaiken_jna_wrapper.dylib  -O native/darwin-aarch64/libaiken_jna_wrapper.dylib

echo "Downloading darwin-x86-64"
wget https://github.com/bloxbean/aiken-jna-wrapper/releases/download/$tag/darwin-x86-64_libaiken_jna_wrapper.dylib -O  native/darwin-x86-64/libaiken_jna_wrapper.dylib

echo "Downloading win32-x86-64"
wget https://github.com/bloxbean/aiken-jna-wrapper/releases/download/$tag/windows-x86-64_aiken_jna_wrapper.dll -O  native/win32-x86-64/aiken_jna_wrapper.dll

echo "Downloading win32-aarch64"
wget https://github.com/bloxbean/aiken-jna-wrapper/releases/download/$tag/windows-aarch64_aiken_jna_wrapper.dll -O  native/win32-aarch64/aiken_jna_wrapper.dll
