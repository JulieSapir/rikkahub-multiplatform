# SQLite simple tokenizer

Source: https://github.com/wangfenjin/simple/tree/v0.6.1
Commit: ea854b0a727e9f15f4878ad0ae9f1b738a92afe4

These files are copied unchanged from v0.6.1. Linux builds the extension with
`SIMPLE_WITH_JIEBA=OFF`, matching the Android libraries recorded in repository
commit 2b522f99. Only the extension, embedded pinyin data and SQLite extension
headers are included; SQLite itself is supplied by AndroidX BundledSQLiteDriver.

Build with CMake and a host C++ compiler. The Gradle resource task bundles the
result for the current Linux architecture; no runtime download is required.
See LICENSE and contrib/sqlite3/LICENSE. The vendored build helper is
contrib/CMakeRC.cmake from the same simple release. Its MIT notice is included
as contrib/CMakeRC-LICENSE.txt, copied from vector-of-bool/cmrc tag 2.0.0
(commit 692890b61220c4889ad84a0bac076e9c4d3101c1).
