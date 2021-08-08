package de.deftk.openww.android.fragments.feature.mail

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.*
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import de.deftk.openww.android.R
import de.deftk.openww.android.api.Response
import de.deftk.openww.android.databinding.FragmentReadMailBinding
import de.deftk.openww.android.fragments.AbstractFragment
import de.deftk.openww.android.utils.CustomTabTransformationMethod
import de.deftk.openww.android.utils.Reporter
import de.deftk.openww.android.utils.TextUtils
import de.deftk.openww.android.viewmodel.MailboxViewModel
import de.deftk.openww.android.viewmodel.UserViewModel
import de.deftk.openww.api.model.Permission
import de.deftk.openww.api.model.feature.mailbox.IEmail
import de.deftk.openww.api.model.feature.mailbox.IEmailFolder
import java.text.DateFormat

class ReadMailFragment : AbstractFragment(true) {

    private val args: ReadMailFragmentArgs by navArgs()
    private val userViewModel: UserViewModel by activityViewModels()
    private val mailboxViewModel: MailboxViewModel by activityViewModels()
    private val navController by lazy { findNavController() }

    private lateinit var binding: FragmentReadMailBinding
    private lateinit var email: IEmail
    private lateinit var emailFolder: IEmailFolder

    private var deleted = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentReadMailBinding.inflate(inflater, container, false)

        mailboxViewModel.emailReadPostResponse.observe(viewLifecycleOwner) { response ->
            enableUI(true)
            if (response != null)
                mailboxViewModel.resetReadPostResponse() // mark as handled
            if (deleted)
                return@observe

            if (response is Response.Failure) {
                Reporter.reportException(R.string.error_read_email_failed, response.exception, requireContext())
                navController.popBackStack()
                return@observe
            } else if (response is Response.Success) {
                response.value?.also { email ->
                    binding.mailSubject.text = email.subject
                    binding.mailAuthor.text = (email.from ?: emptyList()).firstOrNull()?.name ?: ""
                    binding.mailAuthorAddress.text = (email.from ?: emptyList()).firstOrNull()?.address ?: ""
                    binding.mailDate.text = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.DEFAULT).format(email.date)
                    val text = email.text ?: email.plainBody
                    binding.mailMessage.text = TextUtils.parseMultipleQuotes(TextUtils.parseInternalReferences(TextUtils.parseHtml(text), null, navController))
                    binding.mailMessage.movementMethod = LinkMovementMethod.getInstance()
                    binding.mailMessage.transformationMethod = CustomTabTransformationMethod(binding.mailMessage.autoLinkMask)
                }

            }
        }

        mailboxViewModel.emailPostResponse.observe(viewLifecycleOwner) { response ->
            if (response != null)
                mailboxViewModel.resetPostResponse() // mark as handled

            if (response is Response.Success) {
                deleted = true
                navController.popBackStack()
            } else if (response is Response.Failure) {
                Reporter.reportException(R.string.error_save_changes_failed, response.exception, requireContext())
            }
        }

        mailboxViewModel.foldersResponse.observe(viewLifecycleOwner) { folderResponse ->
            if (folderResponse is Response.Success) {
                emailFolder = folderResponse.value.first { it.id == args.folderId }

                val mailResponse = mailboxViewModel.getCachedResponse(emailFolder)
                if (mailResponse is Response.Success) {
                    val foundEmail = mailResponse.value.firstOrNull { it.id == args.mailId }
                    if (foundEmail == null) {
                        Reporter.reportException(R.string.error_email_not_found, args.mailId.toString(), requireContext())
                        navController.popBackStack()
                        return@observe
                    }
                    email = foundEmail
                    userViewModel.apiContext.value?.apply {
                        mailboxViewModel.readEmail(email, emailFolder, this)
                        enableUI(false)
                    }
                } else if (mailResponse is Response.Failure) {
                    Reporter.reportException(R.string.error_get_emails_failed, mailResponse.exception, requireContext())
                }
            } else if (folderResponse is Response.Failure) {
                Reporter.reportException(R.string.error_get_folders_failed, folderResponse.exception, requireContext())
            }
        }

        userViewModel.apiContext.observe(viewLifecycleOwner) { apiContext ->
            if (apiContext == null) {
                navController.popBackStack(R.id.mailFragment, false)
            }
        }
        setHasOptionsMenu(true)
        return binding.root
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        userViewModel.apiContext.value?.also { apiContext ->
            if (apiContext.user.effectiveRights.contains(Permission.MAILBOX_WRITE) || apiContext.user.effectiveRights.contains(Permission.MAILBOX_ADMIN)) {
                inflater.inflate(R.menu.simple_mail_edit_item_menu, menu)
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_item_delete -> {
                userViewModel.apiContext.value?.also { apiContext ->
                    mailboxViewModel.deleteEmail(email, emailFolder, true, apiContext)
                    enableUI(false)
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onUIStateChanged(enabled: Boolean) {}
}