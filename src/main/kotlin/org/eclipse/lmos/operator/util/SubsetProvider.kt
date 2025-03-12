package org.eclipse.lmos.operator.util  

object SubsetProvider {  

    private val canaryReleaseEnabled: Boolean by lazy {  
        val envValue = System.getenv("LMOS_OPERATOR_CANARY_RELEASE_ENABLED")  
        envValue?.toBoolean() ?: true
    }  

    fun getSubset(subsetValue: String?): String {
        return if (canaryReleaseEnabled) {
            subsetValue ?: throw IllegalArgumentException("Subset value is required when canary release is enabled")
        } else {
            subsetValue ?: "stable"
        }
    }  
}  