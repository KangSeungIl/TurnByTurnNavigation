package lbsproject.turnbyturnnavi;

import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.Filterable;

import java.util.ArrayList;

import static lbsproject.turnbyturnnavi.PlacesAutoComplete.autocomplete;

/**
 * Created by kangsi on 2017. 2. 10..
 */

public class CustomAdapter extends ArrayAdapter implements Filterable {
    private ArrayList resultList;

    public CustomAdapter(MapsActivity context, int textViewResourceId) {
        super(context, textViewResourceId);
    }

    @Override
    public int getCount() {
        return resultList.size();
    }

    @Override
    public String getItem(int index) {
        if ((resultList.size() - 1) >= index) {
            return (String) resultList.get(index);
        }
        return null;
    }

    @Override
    public Filter getFilter() {
        Filter filter = new Filter() {

            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults filterResults = new FilterResults();
                ArrayList queryResults;
                if ((constraint != null) && (constraint.length() != 0)) {
                    // Retrieve the autocomplete results.
                    queryResults = autocomplete(constraint.toString());
                } else {
                    queryResults = new ArrayList();
                }
                // Assign the data to the FilterResults
                filterResults.values = queryResults;
                Log.v("size", String.valueOf(queryResults.size()));
                filterResults.count = queryResults.size();

                return filterResults;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                resultList = (ArrayList) results.values;
                if (results != null && results.count > 0) {
                    notifyDataSetChanged();
                } else {
                    notifyDataSetInvalidated();
                }
            }
        };
        return filter;
    }
}
