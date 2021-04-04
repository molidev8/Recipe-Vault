package com.moliverac8.recipevault.ui.recipeList

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.moliverac8.recipevault.databinding.FragmentRecipeListBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RecipeListFragment : Fragment() {

    private val viewModel: RecipeListVM by viewModels(ownerProducer = { this })

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentRecipeListBinding.inflate(layoutInflater)

        val adapter = RecipeListAdapter(RecipeListAdapter.OnClickListener { recipe, isEditable ->
            val action =
                RecipeListFragmentDirections.actionRecipeListFragmentToRecipePagerFragment(recipe.domainRecipe.id, isEditable)
            findNavController().navigate(action)
        })

        viewModel.updateRecipes()

        binding.recipeList.adapter = adapter

        viewModel.recipes.observe(viewLifecycleOwner, {
            adapter.submitList(it)
        })

        binding.newRecipeBtn.setOnClickListener {
            val action = RecipeListFragmentDirections.actionRecipeListFragmentToRecipePagerFragment(-1, true)
            findNavController().navigate(action)
        }

        binding.lifecycleOwner = this
        return binding.root
    }

    override fun onStop() {
        super.onStop()
    }
}