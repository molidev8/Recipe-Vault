package com.moliverac8.recipevault.ui.recipeDetail.edit

import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.FileProvider
import androidx.core.view.forEachIndexed
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputLayout
import com.moliverac8.domain.DietType
import com.moliverac8.domain.DishType
import com.moliverac8.domain.Recipe
import com.moliverac8.domain.RecipeWithIng
import com.moliverac8.recipevault.GENERAL
import com.moliverac8.recipevault.IO
import com.moliverac8.recipevault.PERMISSION
import com.moliverac8.recipevault.R
import com.moliverac8.recipevault.databinding.FragmentRecipeDetailEditBinding
import com.moliverac8.recipevault.ui.REQUEST_IMAGE_CAPTURE
import com.moliverac8.recipevault.ui.common.Permissions
import com.moliverac8.recipevault.ui.common.toJsonInstructions
import com.moliverac8.recipevault.ui.common.toListOfInstructions
import com.moliverac8.recipevault.ui.recipeDetail.RecipeDetailVM
import com.moliverac8.recipevault.ui.recipeDetail.RecipePagerFragment
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class RecipeDetailEditFragment : Fragment() {

    private lateinit var binding: FragmentRecipeDetailEditBinding
    private lateinit var recipe: RecipeWithIng
    private lateinit var instructions: MutableList<String>
    private val viewModel: RecipeDetailVM by viewModels(ownerProducer = { parentFragment as RecipePagerFragment })
    private lateinit var photoUri: Uri
    private val mapOfInstructions = mutableMapOf<Int, String>()
    private var nInstructions = 0
    private lateinit var adapter: RecipeInstructionsEditAdapter
    private lateinit var recipePhotoPath: String
    private val topBar: MaterialToolbar by lazy {
        (requireParentFragment().view as CoordinatorLayout).findViewById(R.id.top_bar)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentRecipeDetailEditBinding.inflate(layoutInflater)
        adapter = RecipeInstructionsEditAdapter()
        binding.instructions.adapter = adapter

        viewModel.recipeWithIng.observe(viewLifecycleOwner, { recipe ->
            this.recipe = recipe
            if (recipe.domainRecipe.name.isNotBlank()) {
                binding.setTitleEdit.setText(recipe.domainRecipe.name)
            }
            if (recipe.domainRecipe.timeToCook != 0) {
                binding.setTimeToCookEdit.setText(recipe.domainRecipe.timeToCook.toString())
            }
            binding.setDescriptionEdit.setText(recipe.domainRecipe.description)
            photoUri = Uri.parse(recipe.domainRecipe.image)
            when (recipe.domainRecipe.dietType) {
                DietType.VEGAN -> binding.veganChip.isChecked = true
                DietType.VEGETARIAN -> binding.vegetarianChip.isChecked = true
                DietType.REGULAR -> binding.regularChip.isChecked = true
            }
            recipe.domainRecipe.dishType.forEach {
                when (it) {
                    DishType.BREAKFAST -> binding.breakfastChip.isChecked = true
                    DishType.MEAL -> binding.mealChip.isChecked = true
                    DishType.DINNER -> binding.dinnerChip.isChecked = true
                }
            }
            instructions =
                if (recipe.domainRecipe.instructions.isNotEmpty())
                    recipe.domainRecipe.instructions.toListOfInstructions().toMutableList()
                else mutableListOf()
            adapter.submitList(instructions)
        })

        binding.photoBtn.setOnClickListener {
            if (!Permissions.hasPermissions(requireContext())) {
                Permissions.requestPermissionsFragment(::requestPermissions)
            } else {
                launchCamera()
                Log.d(PERMISSION, "Permisos concedidos up")
            }
        }

        binding.addBtn.setOnClickListener {
            instructions.add("")
            mapOfInstructions[nInstructions] = ""
            nInstructions += 1
            adapter.submitList(instructions)
            adapter.notifyItemInserted(instructions.size - 1)
        }

        topBar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.save_recipe -> {
                    saveRecipeInfo()
                    findNavController().popBackStack()
                    true
                }
                else -> false
            }
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val watcher = { container: TextInputLayout ->
            { text: Editable? ->
                if (text.isNullOrBlank()) {
                    container.error = getString(R.string.required_text)
                } else {
                    container.isErrorEnabled = false
                }
            }
        }

        val focusListener = { container: TextInputLayout ->
            View.OnFocusChangeListener { v, hasFocus ->
                with(v as EditText) {
                    if (!hasFocus) {
                        if (text.isNullOrBlank()) {
                            container.error = getString(R.string.required_text)
                        } else {
                            container.isErrorEnabled = false
                        }
                    }
                }
            }
        }

        binding.setTitleEdit.doAfterTextChanged(watcher(binding.setTitle))
        binding.setTimeToCookEdit.doAfterTextChanged(watcher(binding.setTimeToCook))
        binding.setTitleEdit.onFocusChangeListener = focusListener(binding.setTitle)
        binding.setTimeToCookEdit.onFocusChangeListener = focusListener(binding.setTimeToCook)
    }

    private fun saveRecipeInfo() {
        // Compruebo si es una receta nueva o una edicion
        val type = mutableListOf<DishType>()
        val id = if (recipe.domainRecipe.id == -1) -1
        else recipe.domainRecipe.id

        // Recupero las instrucciones
        recoverInstructions()

        // Obtengo el cuando se quiere comer la receta
        binding.timeToEatChips.checkedChipIds.forEach {
            when (it) {
                binding.breakfastChip.id -> type.add(DishType.BREAKFAST)
                binding.mealChip.id -> type.add(DishType.MEAL)
                else -> type.add(DishType.DINNER)
            }
        }

        // Obtengo el tipo de dieta
        val diet = when (binding.dietChips.checkedChipId) {
            binding.regularChip.id -> DietType.REGULAR
            binding.veganChip.id -> DietType.VEGETARIAN
            else -> DietType.VEGAN
        }

        // Guardo en la base de datos
        viewModel.saveRecipe(
            Recipe(
                id,
                binding.setTitleEdit.text.toString(),
                binding.setTimeToCookEdit.text.toString().toInt(),
                type,
                diet,
                mapOfInstructions.values.toList().toJsonInstructions(),
                photoUri.toString(),
                binding.setDescriptionEdit.text.toString()
            )
        )
        if (id != -1) viewModel.updateRecipe(recipe)
        else viewModel.saveEverything()
    }

    private fun recoverInstructions() {
        binding.instructions.forEachIndexed { idx, view ->
            if (view is TextInputLayout) {
                Log.d(GENERAL, view.editText?.text.toString())
                mapOfInstructions[idx] = view.editText?.text.toString()
            }
        }
    }

    private fun launchCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val photoFile: File? = try {
            createImageFile()
        } catch (ex: IOException) {
            Log.d(IO, "Error al lanzar la camara")
            null
        }
        photoUri = FileProvider.getUriForFile(
            requireContext(),
            "com.moliverac8.recipevault",
            photoFile!!
        )

        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
        startActivityForResult(intent, REQUEST_IMAGE_CAPTURE)
    }

    @Throws(IOException::class)
    @SuppressLint("SimpleDateFormat")
    private fun createImageFile(): File {
        val timestamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File? = context?.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timestamp}_",
            ".jpg",
            storageDir
        ).apply {
            recipePhotoPath = absolutePath
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Glide.with(this).load(photoUri).into(binding.photoBtn)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == Permissions.PERMISSION_CODE) {
            if (grantResults.isEmpty() || (grantResults.any { it == PackageManager.PERMISSION_DENIED })) {
                Log.d(PERMISSION, "Permisos denegados")
            } else {
                launchCamera()
                Log.d(PERMISSION, "Permisos concedidos")
            }
        }
    }

    companion object {
        fun newInstance(): RecipeDetailEditFragment = RecipeDetailEditFragment()
    }
}