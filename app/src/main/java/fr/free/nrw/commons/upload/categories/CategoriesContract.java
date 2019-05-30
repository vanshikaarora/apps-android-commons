package fr.free.nrw.commons.upload.categories;

import fr.free.nrw.commons.BasePresenter;
import fr.free.nrw.commons.category.CategoryClickedListener;
import fr.free.nrw.commons.category.CategoryItem;

import java.util.List;

/**
 * The contract with with UploadCategoriesFragment and its presenter would talk to each other
 */
public interface CategoriesContract {

    public interface View {

        void showProgress(boolean shouldShow);

        void showError(String error);

        void showError(int stringResourceId);

        void setCategories(List<CategoryItem> categories);

        void addCategory(CategoryItem category);

        void goToNextScreen();

        void showNoCategorySelected();

        void setSelectedCategories(List<CategoryItem> selectedCategories);
    }

    public interface UserActionListener extends BasePresenter<View> {

        void searchForCategories(String query, List<String> imageTitleList);

        void verifyCategories();

        void onCategoryItemClicked(CategoryItem categoryItem);
    }


}
