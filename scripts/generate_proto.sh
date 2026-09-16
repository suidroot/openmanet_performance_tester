#!/bin/sh
# Regenerates app/src/main/kotlin-gen from the vendored .proto files in
# app/src/main/proto. Uses local plugins (protoc's built-in java/kotlin
# generators + the connect-kotlin jar in .tools/) instead of buf.build's
# remote codegen, which rate-limits aggressively on repeated runs.
#
# Requires: buf, protoc (`brew install bufbuild/buf/buf protobuf`), and a
# JDK on PATH (for .tools/protoc-gen-connect-kotlin).
#
# --path lists every file whose types this app uses PLUS every proto file
# each of those types transitively depends on (buf's --path does not follow
# cross-file message dependencies automatically - see openmanet/network/v1/node.proto,
# needed because openmanet.service.v1.Node embeds openmanet.network.v1.Node).
# Add more --path lines here as more RPCs are wired up in later phases.
set -eu
cd "$(dirname "$0")/.."

rm -rf app/src/main/kotlin-gen
buf generate \
  --path app/src/main/proto/buf/validate/validate.proto \
  --path app/src/main/proto/openmanet/network/v1/node.proto \
  --path app/src/main/proto/openmanet/service/v1/node.proto \
  --path app/src/main/proto/openmanet/service/v1/mesh.proto \
  --path app/src/main/proto/openmanet/service/v1/status.proto \
  --path app/src/main/proto/openmanet/service/v1/interface.proto \
  --path app/src/main/proto/openmanet/service/v1/station.proto \
  --path app/src/main/proto/openmanet/gnss/v1/gnss_service.proto \
  --path app/src/main/proto/openmanet/gnss/v1/gnss.proto

echo "Generated $(find app/src/main/kotlin-gen -type f | wc -l | tr -d ' ') files in app/src/main/kotlin-gen"
