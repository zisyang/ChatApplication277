package com.example.android.chatapplication

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.TextUtils
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference

class ChatActivity : AppCompatActivity() {

    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var messageBox: EditText
    private lateinit var sendButton: ImageView
    private lateinit var attachmentButton: ImageView
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var messageList: ArrayList<Message>
    private lateinit var mDbRef: DatabaseReference

    var receiverRoom: String? = null
    var senderRoom: String? = null

    private val TAG = "ChatActivity->FileSharing"
    private var CHOOSING_IMAGE_REQUEST = 1234
    private var fileUri: Uri? = null
    private var sharesRefence: StorageReference? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        val name = intent.getStringExtra("name")
        val receiverUid = intent.getStringExtra("uid")

        val senderUid = FirebaseAuth.getInstance().currentUser?.uid
        mDbRef = FirebaseDatabase.getInstance().getReference()

        senderRoom = receiverUid + senderUid
        receiverRoom = senderUid + receiverUid

        supportActionBar?.title = name

        chatRecyclerView = findViewById(R.id.chatRecyclerView)
        messageBox = findViewById(R.id.messageBox)
        sendButton = findViewById(R.id.sentButton)
        messageList = ArrayList()
        messageAdapter = MessageAdapter(this,messageList)

        chatRecyclerView.layoutManager = LinearLayoutManager(this)
        chatRecyclerView.adapter = messageAdapter

        sharesRefence = FirebaseStorage.getInstance().reference.child("shares")
        attachmentButton = findViewById(R.id.attachmentButton)

        //logic for adding data to recyclerview
        mDbRef.child("chats").child(senderRoom!!).child("messages")
            .addValueEventListener(object: ValueEventListener{
                override fun onDataChange(snapshot: DataSnapshot) {

                    messageList.clear()

                    for (postSnapshot in snapshot.children) {
                        val message = postSnapshot.getValue(Message::class.java)
                        messageList.add(message!!)
                    }
                    messageAdapter.notifyDataSetChanged()
                }
                override fun onCancelled(error: DatabaseError) {

                }

            })

        // Adding the message to the database
        sendButton.setOnClickListener {

        val message = messageBox.text.toString()
        val messageObject = Message(message,senderUid)

        mDbRef.child("chats").child(senderRoom!!).child("messages").push()
            .setValue(messageObject).addOnSuccessListener {
                mDbRef.child("chats").child(receiverRoom!!).child("messages").push()
                    .setValue(messageObject)

            }
            messageBox.setText("")

        }

        attachmentButton.setOnClickListener{
            showChoosingFile()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if(requestCode == CHOOSING_IMAGE_REQUEST && resultCode == RESULT_OK &&
            data != null && data.data != null) {
            fileUri = data.data
            var fileName = fileUri?.let { getFileName(it) }
            fileName?.let { uploadFile(it) }

        }
    }

    private fun showChoosingFile() {
        val intent = Intent()
        intent.type = "application/pdf"
        intent.action = Intent.ACTION_GET_CONTENT
        startActivityForResult(Intent.createChooser(intent, "Select File"), CHOOSING_IMAGE_REQUEST)
    }

    @SuppressLint("Range")
    private fun getFileName(uri: Uri): String{  //full name with extension
        var fileDisplayName = ""
        if(uri != null) {
            if(fileUri?.scheme.equals("content")) {
                val contentResolver = contentResolver
                var cursor = fileUri?.let { contentResolver.query(it, null, null, null, null) }
                try {
                    if (cursor != null && cursor.moveToFirst()) {
                        fileDisplayName = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                    }
                }finally {
                    cursor?.close()
                }
            }
        }
        return fileDisplayName.split(".")[0]
    }

    private fun getFileExtension(uri: Uri): String {
        val contentResolver = contentResolver
        val mime = MimeTypeMap.getSingleton()

        return mime.getExtensionFromMimeType(contentResolver.getType(uri)).toString()
    }

    private fun uploadFile(fileName: String) {
        if(fileUri != null) {
            val fileRef = sharesRefence!!.child(fileName + "." + getFileExtension(fileUri!!))
            fileRef.putFile(fileUri!!)
                .addOnProgressListener { taskSnapshot ->
                    val progress = 100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount
                    val intProgress = progress.toInt()
                    Toast.makeText(this, "Uploaded $intProgress %...", Toast.LENGTH_SHORT).show()
                }
                .addOnPausedListener {
                    Toast.makeText(this, "Upload is paused!", Toast.LENGTH_LONG).show()
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(this, exception.message, Toast.LENGTH_SHORT).show()
                }
                .addOnSuccessListener { taskSnapshot ->
                    Log.i(TAG, "Name: " + taskSnapshot.metadata!!.name)
                    Log.i(TAG, taskSnapshot.metadata!!.path + " - " +
                            taskSnapshot.metadata!!.sizeBytes / 1024 + " KBs")
                    Toast.makeText(this, "File Uploaded ", Toast.LENGTH_LONG).show()
                }
                .continueWithTask { task ->
                    if(!task.isSuccessful) {
                        task.exception?.let {
                            throw it
                        }
                    }
                    fileRef.downloadUrl
                }
                .addOnCompleteListener { task ->
                    if(task.isComplete) {
                        val downloadUrl = task.result
                        Log.i(TAG, "URL: $downloadUrl")

                        val senderUid = FirebaseAuth.getInstance().currentUser?.uid
                        val message = downloadUrl.toString()
                        val messageObject = Message(message,senderUid)

                        mDbRef.child("chats").child(senderRoom!!).child("messages").push()
                            .setValue(messageObject).addOnSuccessListener {
                                mDbRef.child("chats").child(receiverRoom!!).child("messages").push()
                                    .setValue(messageObject)

                            }
                    }
                }
        }
    }
}