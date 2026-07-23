//import at.asitplus.wallet.lib.iso.zk.IsoMdocZkProofRegistry
//import at.asitplus.iso.zk.longfellowZk.IsoMdocLongfellowZKProof
//import at.asitplus.testballoon.FreeSpec
//import at.asitplus.wallet.eupid.Initializer
//import de.infix.testBalloon.framework.core.TestInvocation
//import de.infix.testBalloon.framework.core.TestSession
//import de.infix.testBalloon.framework.core.invocation
//import de.infix.testBalloon.framework.core.testScope
//import io.github.aakira.napier.DebugAntilog
//import io.github.aakira.napier.Napier
//import io.kotest.core.spec.style.FreeSpec
//
//class TestConfig : TestSession(
//    testConfig = DefaultConfiguration.invocation(TestInvocation.CONCURRENT)
//        .testScope(isEnabled = false)
//) {
//    init {
//        Napier.takeLogarithm()
//        Napier.base(DebugAntilog())
//        Initializer.initWithVCK()
//        at.asitplus.wallet.mdl.Initializer.initWithVCK()
//
//        // Register Longfellow ZK proof system for tests
//        IsoMdocZkProofRegistry.register(IsoMdocLongfellowZKProof.Default).onFailure {
//            Napier.e("Failed to register Longfellow ZK proof factory: ${it.message}", it)
//        }
//
//        FreeSpec.defaultTestNameMaxLength = 10 //work around Android test name length limit
//        FreeSpec.defaultDisplayNameMaxLength = 32 //work around Android test name length limit
//    }
//}
