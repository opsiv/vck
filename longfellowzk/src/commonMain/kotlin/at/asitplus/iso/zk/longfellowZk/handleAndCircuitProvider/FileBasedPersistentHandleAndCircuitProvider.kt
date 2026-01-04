package at.asitplus.iso.zk.longfellowZk.handleAndCircuitProvider

import at.asitplus.iso.zk.longfellowZk.ZkParams
import at.asitplus.iso.zk.longfellowZk.ZkSpecHandle
import io.github.aakira.napier.Napier
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray


// TODO: Create a more solid file-based or DB-based provider with prefilled circuits
class FileBasedPersistentHandleAndCircuitProvider(
    private val delegate: HandleAndCircuitProvider,
    private val baseDir: Path
) : HandleAndCircuitProvider {
    private val circuitCache = mutableMapOf<String, ByteArray>()
    private val handleCache = mutableMapOf<String, ZkSpecHandle>()

    override fun getHandle(params: ZkParams): ZkSpecHandle {
        return handleCache.getOrPut(params.circuitId) {
            Napier.d("Fetching circuit handle fir ${params.circuitId}")
            delegate.getHandle(params)
        }
    }

    override fun getCircuit(params: ZkParams): ByteArray {
        val key = params.circuitId
        return circuitCache.getOrPut(key) {
            val path = Path(baseDir, "$key.circuit")
            if (SystemFileSystem.exists(path)) {
                Napier.d("Loading circuit from file cache : $path")
                return@getOrPut SystemFileSystem.source(path).buffered().readByteArray()
            }
            val circuit = delegate.getCircuit(params)
            try {
                if (!SystemFileSystem.exists(path)) {
                    SystemFileSystem.createDirectories(baseDir)
                }
                SystemFileSystem.sink(path).buffered().use { it.write(circuit) }
                Napier.d("Stored circuit to file $path")
            } catch (e: Exception) {
                Napier.d("Could not persist circuit to file: ${e.message}")
            }
            circuit
        }
    }
}
