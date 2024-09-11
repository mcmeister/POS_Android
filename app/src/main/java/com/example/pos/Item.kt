package com.example.pos

import android.os.Parcel
import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "item")
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,  // Primary key with auto-generation
    val name: String,                                  // Item name
    val rawPrice: Int,                                 // Raw price (changed to Int)
    val salePrice: Int,                                // Sale price (changed to Int)
    val photoUri: String?                              // Photo URI, nullable
) : Parcelable {

    // Parcelable constructor
    private constructor(parcel: Parcel) : this(
        id = parcel.readInt(),                         // Reading 'id' from Parcel
        name = parcel.readString() ?: "",              // Reading 'name' from Parcel, fallback if null
        rawPrice = parcel.readInt(),                   // Reading 'rawPrice' from Parcel
        salePrice = parcel.readInt(),                  // Reading 'salePrice' from Parcel
        photoUri = parcel.readString()                 // Reading 'photoUri' from Parcel, can be null
    )

    // Writing data to Parcel
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(id)                            // Writing 'id' to Parcel
        parcel.writeString(name)                       // Writing 'name' to Parcel
        parcel.writeInt(rawPrice)                      // Writing 'rawPrice' to Parcel
        parcel.writeInt(salePrice)                     // Writing 'salePrice' to Parcel
        parcel.writeString(photoUri)                   // Writing 'photoUri' to Parcel
    }

    // Required method for Parcelable
    override fun describeContents(): Int {
        return 0
    }

    // Parcelable CREATOR object
    companion object CREATOR : Parcelable.Creator<Item> {
        override fun createFromParcel(parcel: Parcel): Item {
            return Item(parcel)                        // Create Item from Parcel
        }

        override fun newArray(size: Int): Array<Item?> {
            return arrayOfNulls(size)                  // Create an array of null items
        }
    }
}
