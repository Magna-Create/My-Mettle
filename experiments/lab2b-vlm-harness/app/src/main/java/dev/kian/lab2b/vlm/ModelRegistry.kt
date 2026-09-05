package dev.kian.lab2b.vlm

enum class ComputeBackend { CPU, GPU }
enum class RuntimeAdapter { MNN_3_6_1 }
data class ModelAsset(val name: String, val sizeBytes: Long, val sha256: String)
data class HarnessModelSpec(
    val id: String, val displayName: String, val modelSource: String, val revision: String,
    val files: List<ModelAsset>, val license: String = "Apache-2.0",
    val runtimeAdapter: RuntimeAdapter = RuntimeAdapter.MNN_3_6_1,
    val format: String = "MNN graph + external weights + tokenizer + vision graph/weights",
    val supportsImage: Boolean = true,
    val supportedBackends: Set<ComputeBackend> = setOf(ComputeBackend.CPU, ComputeBackend.GPU),
    val defaultBackend: ComputeBackend = ComputeBackend.CPU,
    val contextLength: Int = 8192,
    val systemMode: SystemPromptMode = SystemPromptMode.TRUE_SYSTEM_ROLE,
    val routeUnavailable: String? = null,
) {
    val sizeBytes: Long get() = files.sumOf { it.sizeBytes }
    fun url(asset: ModelAsset) = "https://huggingface.co/$modelSource/resolve/$revision/${asset.name}"
    val fingerprint: String get() = Hashing.sha256((revision + files.joinToString { "${it.name}:${it.sizeBytes}:${it.sha256}" }).toByteArray())
}

object ModelRegistry {
    val models = listOf(
        HarnessModelSpec(
            "gemma4-e2b", "Gemma 4 E2B IT", "taobao-mnn/gemma-4-E2B-it-MNN", "ce18884f154ce405545f1acda5c5c8fdd9c1280c",
            listOf(
                ModelAsset("config.json", 678L, "3b1c8caafa2792a64b81d2ef47d3e6afc1c250b280389e77d0d25628108c87a7"),
                ModelAsset("llm.mnn", 2276992L, "7115ecd7a66332d8a14c9d6467d560baec33c9650174cbb2f0e7641a69999216"),
                ModelAsset("llm.mnn.weight", 1436474178L, "8d4b0fabb015da09a820fab22714f392b9e73f8f2fc7175dea7ef4f581d03881"),
                ModelAsset("llm_config.json", 1415L, "7096f286d274bee7f374b7d06533d5a611f6d678b119fa9542e74e65fd8a5379"),
                ModelAsset("ple_embeddings_int4.bin", 1468006400L, "c76e660ca418790bde8757099af0144488ece631dcd612245f1e1bf801f9e1e3"),
                ModelAsset("tokenizer.mtok", 10068633L, "e08a1293e250750949bb1f543edd626cc6cf9f039a2e461958d20f33407d26b9"),
                ModelAsset("visual.mnn", 1060528L, "759a3fa521cbb9e4bcf877769524faa41f0e1288a61d664cb9656f3e70f61fb0"),
                ModelAsset("visual.mnn.weight", 225904812L, "308e356f5a8527c28c1caba233b8d3521d4ba558b56cbcb8a53ed103d73ae1af"),
            ),
        ),
        HarnessModelSpec(
            "qwen35-2b", "Qwen3.5-2B", "taobao-mnn/Qwen3.5-2B-MNN", "35781816d7b6a9dcb273a6765ac9563401951c3c",
            listOf(
                ModelAsset("config.json", 652L, "92853033efe602f95efca3e1c05cd8b108f973c8beed417843a9671f8147ed8d"),
                ModelAsset("llm.mnn", 2148136L, "23df98f8b341b277365e0bbca025c1d192939e3d32d7f79776352c6f32e77960"),
                ModelAsset("llm.mnn.weight", 1176647702L, "c93f71a2dbecf9328782bd38861656d8faa82e95e7f99607350074768a482054"),
                ModelAsset("llm_config.json", 8692L, "a88234b36c2af0eff8e5c89667011badf71c15e30459eb0e21030a8f3f9ed240"),
                ModelAsset("tokenizer.txt", 6465727L, "7e75de1f279a10b65bd9dc1a5207205cb8993823861c4c42bbbd74e48e1c23a4"),
                ModelAsset("visual.mnn", 488096L, "88fc40a7b676e90eb2cb86d854db15cb90b9eb1f34087ab0f48c5e43572c8dac"),
                ModelAsset("visual.mnn.weight", 195587264L, "8f90e106f5b9ae9a939faed240305cfdd5c6740ae91d3fc418a990bee0cce36b"),
            ),
        ),
        HarnessModelSpec(
            "qwen3vl-2b", "Qwen3-VL-2B-Instruct", "taobao-mnn/Qwen3-VL-2B-Instruct-MNN", "9e49ec71ded22500a997ed0f9961e1e92b85bbc9",
            listOf(
                ModelAsset("config.json", 605L, "1ed5c6e65459fdc4b0c33319715b763005013ba8580dd3c687bd2651546ca2a4"),
                ModelAsset("llm.mnn", 462464L, "c2286f60cbd56a82f26bfeac92f6a96e9690889b1939346abfe9e1fae996a8f3"),
                ModelAsset("llm.mnn.weight", 1231860194L, "1554f9ce71743b56c2d7fba4cb0c2a31c7cddf4f21e1a2ff5a2e85b9a316a29f"),
                ModelAsset("llm_config.json", 6445L, "5408721c81cc9a7ea8aa485a0652e5e1a47dd5ea5bbd5af2e1f16bc4f6358699"),
                ModelAsset("tokenizer.txt", 3193555L, "7119de4966cc6a8ae87d7f083e65b315282d06c3122fdd41ce783fdd2d3c1ca2"),
                ModelAsset("visual.mnn", 502512L, "c489c1f65dc6aa5bcee42b3e291f7987df1111423c1fe570d0f3394e1207d2bb"),
                ModelAsset("visual.mnn.weight", 238226780L, "9feb04848cafad1117a510b43d6c2b58d6c31bef1040598156d266f9b42f581f"),
            ),
        ),
    )
    fun get(id: String): HarnessModelSpec = models.single { it.id == id }
}
