package com.calcite.notes.ui.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.calcite.notes.R
import com.calcite.notes.data.local.AppDataStore
import com.calcite.notes.data.local.database.AppDatabase
import com.calcite.notes.data.remote.RetrofitClient
import com.calcite.notes.data.repository.AuthRepository
import com.calcite.notes.databinding.FragmentLoginBinding
import com.calcite.notes.utils.Result

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LoginViewModel by viewModels {
        val apiService = RetrofitClient.getApiService(requireContext())
        val appDataStore = AppDataStore(requireContext())
        val repository = AuthRepository(apiService, appDataStore)
        LoginViewModel.Factory(repository, appDataStore)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnLogin.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            viewModel.login(username, password)
        }

        binding.tvGoRegister.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_registerFragment)
        }

        viewModel.loginResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is Result.Loading -> {
                    binding.btnLogin.isEnabled = false
                    binding.progressBar.visibility = View.VISIBLE
                }

                is Result.Success -> {
                    binding.btnLogin.isEnabled = true
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), "登录成功", Toast.LENGTH_SHORT).show()
                    val noteId = viewModel.navigateToNoteId.value
                    if (noteId != null && noteId > 0) {
                        val bundle = Bundle().apply { putLong("noteId", noteId) }
                        findNavController().navigate(R.id.action_loginFragment_to_noteEditorFragment, bundle)
                    } else {
                        findNavController().navigate(R.id.action_loginFragment_to_homeFragment)
                    }
                }

                is Result.Error -> {
                    binding.btnLogin.isEnabled = true
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
