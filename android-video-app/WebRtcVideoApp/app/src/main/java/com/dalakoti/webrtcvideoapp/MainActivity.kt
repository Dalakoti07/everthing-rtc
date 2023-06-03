package com.dalakoti.webrtcvideoapp

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import com.dalakoti.webrtcvideoapp.databinding.ActivityMainBinding
import com.permissionx.guolindev.PermissionX

class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.enterBtn.setOnClickListener {
            PermissionX.init(this)
                .permissions(
                    android.Manifest.permission.RECORD_AUDIO,
                    android.Manifest.permission.CAMERA
                ).request{ allGranted, _ ,_ ->
                    if (allGranted){
                        startActivity(
                            Intent(this,CallActivity::class.java)
                                .putExtra("username",binding.username.text.toString())
                        )
                    } else {
                        Toast.makeText(this,"you should accept all permissions",Toast.LENGTH_LONG).show()
                    }
                }
        }
    }
}