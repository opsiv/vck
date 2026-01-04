# Populating the native directory
The native directory currently needs to be manually populated according to the following scheme:
```
native
├── linux-amd64
│   └── longfellow_mdoc.so
└── macosx-aarch64
    └── longfellow_mdoc.dylib
```

The binaries must be built for the target in question and share the same name (except for the suffix)