package com.smartnoti.app.data.fake

import com.smartnoti.app.domain.model.RuleUiModel
import com.smartnoti.app.ui.preview.PreviewData

class FakeRuleRepository {
    fun getRules(): List<RuleUiModel> = PreviewData.rules
}
