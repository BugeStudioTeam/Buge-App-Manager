// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager.model

import android.os.Parcel
import android.os.Parcelable

data class CustomLabel(
    val id: String,
    val name: String,
    val appPackages: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
) : Parcelable {

    constructor(parcel: Parcel) : this(
        id = parcel.readString() ?: "",
        name = parcel.readString() ?: "",
        appPackages = parcel.createStringArrayList() ?: emptyList(),
        createdAt = parcel.readLong()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(name)
        parcel.writeStringList(appPackages)
        parcel.writeLong(createdAt)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<CustomLabel> {
        override fun createFromParcel(parcel: Parcel): CustomLabel = CustomLabel(parcel)
        override fun newArray(size: Int): Array<CustomLabel?> = arrayOfNulls(size)
    }
}