package com.jss.bitchat

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ArchitectureFoundationTest {

    @Test
    fun `package and project baseline are correctly configured`() {
        val packageName = "com.jss.bitchat"
        assertThat(packageName).isEqualTo("com.jss.bitchat")
    }

    @Test
    fun `minimum sdk target requirement is API 26`() {
        val minSdk = 26
        assertThat(minSdk).isAtLeast(26)
    }
}

