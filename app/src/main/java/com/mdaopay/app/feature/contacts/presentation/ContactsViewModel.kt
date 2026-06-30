package com.mdaopay.app.feature.contacts.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdaopay.app.core.datastore.Contact
import com.mdaopay.app.core.datastore.ContactsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val contactsStore: ContactsStore
) : ViewModel() {

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    init {
        viewModelScope.launch {
            contactsStore.contactsFlow.collect { _contacts.value = it }
        }
    }

    fun removeContact(id: String) {
        viewModelScope.launch {
            contactsStore.removeContact(id)
        }
    }

    fun toggleFavorite(id: String) {
        viewModelScope.launch {
            contactsStore.toggleFavorite(id)
        }
    }
}
