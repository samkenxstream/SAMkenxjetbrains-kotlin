// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.ui.combobox

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ui.configuration.SdkComboBoxBase
import com.intellij.openapi.roots.ui.configuration.SdkListItem
import com.intellij.openapi.roots.ui.configuration.SdkListModel
import com.intellij.openapi.roots.ui.configuration.SdkListPresenter

class SdkComboBox(model: SdkComboBoxModel) : SdkComboBoxBase<SdkListItem>(model.modelBuilder) {
  val model get() = getModel() as SdkComboBoxModel

  override fun onModelUpdated(listModel: SdkListModel) {
    setModel(model.copyAndSetListModel(listModel))
  }

  override fun onNewSdkAdded(sdk: Sdk) {
    reloadModel()
    setSelectedSdk(sdk)
  }

  override fun setSelectedItem(anObject: Any?) {
    if (anObject is SdkListItem.ActionableItem) {
      anObject.executeAction()
    }
    super.setSelectedItem(anObject)
    reloadModel()
  }

  fun setSelectedSdk(sdk: Sdk?) {
    val sdkItem = sdk?.let { model.listModel.findSdkItem(sdk) }
    selectedItem = when {
      sdk == null -> showNoneSdkItem()
      sdkItem == null -> showInvalidSdkItem(sdk.name)
      else -> sdkItem
    }
  }

  fun getSelectedSdk(): Sdk? {
    return when (val it = selectedItem) {
      is SdkListItem.ProjectSdkItem -> model.sdksModel.projectSdk
      is SdkListItem.SdkItem -> it.sdk
      else -> null
    }
  }

  override fun reloadModel() {
    super.reloadModel()
    myModel.reloadActions(this, getSelectedSdk())
    //myModel.detectItems(this) -- Cannot find DialogWrapper parent for this comboBox
  }

  init {
    setModel(model)
    setRenderer(object : SdkListPresenter(this.model.sdksModel) {
      override fun getModel(): SdkListModel = this@SdkComboBox.model.listModel
      override fun showProgressIcon() = this@SdkComboBox.isPopupVisible
    })
    reloadModel()
  }
}