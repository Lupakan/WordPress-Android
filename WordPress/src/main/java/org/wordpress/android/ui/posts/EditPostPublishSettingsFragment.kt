package org.wordpress.android.ui.posts

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context.ALARM_SERVICE
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import org.wordpress.android.R
import org.wordpress.android.WordPress
import org.wordpress.android.fluxc.model.PostModel
import org.wordpress.android.ui.posts.EditPostSettingsFragment.EditPostActivityHook
import org.wordpress.android.ui.posts.PostNotificationTimeDialogFragment.NotificationTime
import org.wordpress.android.util.ToastUtils
import org.wordpress.android.util.ToastUtils.Duration.SHORT
import javax.inject.Inject

class EditPostPublishSettingsFragment : Fragment() {
    private lateinit var addToCalendarContainer: LinearLayout
    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var viewModel: EditPostPublishSettingsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (activity!!.applicationContext as WordPress).component().inject(this)
        viewModel = ViewModelProviders.of(activity!!, viewModelFactory)
                .get(EditPostPublishSettingsViewModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.edit_post_published_settings_fragment, container, false) as ViewGroup
        val dateAndTime = rootView.findViewById<TextView>(R.id.publish_time_and_date)
        val dateAndTimeContainer = rootView.findViewById<LinearLayout>(R.id.publish_time_and_date_container)
        val publishNotification = rootView.findViewById<TextView>(R.id.publish_notification)
        val publishNotificationTitle = rootView.findViewById<TextView>(R.id.publish_notification_title)
        val publishNotificationContainer = rootView.findViewById<LinearLayout>(R.id.publish_notification_container)
        addToCalendarContainer = rootView.findViewById(R.id.post_add_to_calendar_container)

        dateAndTimeContainer.setOnClickListener { showPostDateSelectionDialog() }
        publishNotificationContainer.setOnClickListener { viewModel.showNotification() }

        viewModel.onDatePicked.observe(this, Observer {
            it?.applyIfNotHandled {
                showPostTimeSelectionDialog()
            }
        })
        viewModel.onPublishedDateChanged.observe(this, Observer {
            it?.let { date ->
                viewModel.updatePost(date, getPost())
            }
        })
        viewModel.onNotificationTime.observe(this, Observer {
            it?.let { notificationTime ->
                viewModel.updateUiModel(notificationTime, getPost())
            }
        })
        viewModel.onUiModel.observe(this, Observer {
            it?.let { uiModel ->
                dateAndTime.text = uiModel.publishDateLabel
                publishNotificationTitle.isEnabled = uiModel.notificationEnabled
                publishNotification.isEnabled = uiModel.notificationEnabled
                publishNotificationContainer.isEnabled = uiModel.notificationEnabled
                if (uiModel.notificationEnabled) {
                    publishNotificationContainer.setOnClickListener {
                        viewModel.onShowDialog(getPost())
                    }
                } else {
                    publishNotificationContainer.setOnClickListener(null)
                }
                publishNotification.setText(uiModel.notificationLabel)
                publishNotificationContainer.visibility = if (uiModel.notificationVisible) View.VISIBLE else View.GONE
            }
        })
        viewModel.onShowNotificationDialog.observe(this, Observer {
            showNotificationTimeSelectionDialog(it)
        })
        viewModel.onToast.observe(this, Observer {
            it?.applyIfNotHandled {
                ToastUtils.showToast(
                        context,
                        this,
                        SHORT,
                        Gravity.TOP
                )
            }
        })
        viewModel.onNotificationAdded.observe(this, Observer { event ->
            event?.getContentIfNotHandled()?.let { notification ->
                activity?.let {
                    NotificationManagerCompat.from(it).cancel(notification.id)
                    val notificationIntent = Intent(it, PublishNotificationReceiver::class.java)
                    notificationIntent.putExtra(PublishNotificationReceiver.NOTIFICATION_ID, notification.id)
                    notificationIntent.putExtra(PublishNotificationReceiver.NOTIFICATION_TITLE, notification.title)
                    notificationIntent.putExtra(PublishNotificationReceiver.NOTIFICATION_MESSAGE, notification.message)
                    val pendingIntent = PendingIntent.getBroadcast(
                            it,
                            notification.id,
                            notificationIntent,
                            PendingIntent.FLAG_CANCEL_CURRENT
                    )

                    val alarmManager = it.getSystemService(ALARM_SERVICE) as AlarmManager
//                    alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, notification.scheduledTime, pendingIntent)

                    alarmManager.set(AlarmManager.RTC_WAKEUP, notification.scheduledTime, pendingIntent)
                }
            }
        })
        viewModel.start(getPost())
        return rootView
    }

    private fun showPostDateSelectionDialog() {
        if (!isAdded) {
            return
        }

        val fragment = PostDatePickerDialogFragment.newInstance()
        fragment.show(activity!!.supportFragmentManager, PostDatePickerDialogFragment.TAG)
    }

    private fun showPostTimeSelectionDialog() {
        if (!isAdded) {
            return
        }

        val fragment = PostTimePickerDialogFragment.newInstance()
        fragment.show(activity!!.supportFragmentManager, PostTimePickerDialogFragment.TAG)
    }

    private fun showNotificationTimeSelectionDialog(notificationTime: NotificationTime?) {
        if (!isAdded) {
            return
        }

        val fragment = PostNotificationTimeDialogFragment.newInstance(notificationTime)
        fragment.show(activity!!.supportFragmentManager, PostNotificationTimeDialogFragment.TAG)
    }

    private fun getPost(): PostModel? {
        return getEditPostActivityHook()?.post
    }

    private fun getEditPostActivityHook(): EditPostActivityHook? {
        val activity = activity ?: return null

        return if (activity is EditPostActivityHook) {
            activity
        } else {
            throw RuntimeException("$activity must implement EditPostActivityHook")
        }
    }

    companion object {
        fun newInstance() = EditPostPublishSettingsFragment()
    }
}
