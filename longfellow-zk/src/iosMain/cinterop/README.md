# Populating the cinterop directory
The cinterop directory currently needs to be manually populated as follows:
```
cinterop
├── libs
│   ├── iosArm64
│   │   └── liblongfellow_mdoc.dylib
│   ├── iosSimulatorArm
│   │   └── liblongfellow_mdoc.dylib
│   └── iosX64
│       └── liblongfellow_mdoc.dylib
├── longfellow.def
└── mdoc_zk.h
```

The binaries must be built for the target in question.