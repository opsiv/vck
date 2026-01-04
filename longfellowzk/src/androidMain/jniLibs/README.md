# Populating the jniLibs directory
The jniLibs directory currently needs to be manually populated according to the following scheme:
```
jniLibs
├── arm64-v8a
│   └── longfellow_mdoc.so
└── x86_64
    └── longfellow_mdoc.so
```

The binaries must be built for the target in question and share the same name.