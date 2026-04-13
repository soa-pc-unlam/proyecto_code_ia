package com.ashencostha.mqtt;

import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.Toast;

public class MatrixAdapter extends BaseAdapter {

    private final Context context;
    private int[][] matrix;
    private final OnCellEditListener listener;

    private int selectedRow = -1;
    private int selectedCol = -1;
    private boolean isEditing = false;

    public interface OnCellEditListener {
        void onCellEdited(int row, int col, int value);
    }

    public MatrixAdapter(Context context, int[][] matrix, OnCellEditListener listener) {
        this.context = context;
        this.matrix = matrix;
        this.listener = listener;
    }

    public void setSelection(int row, int col) {
        selectedRow = row;
        selectedCol = col;
        notifyDataSetChanged();
    }

    public void setEditing(boolean editing) {
        isEditing = editing;
        if (!editing) {
            setSelection(-1, -1);
        }
        notifyDataSetChanged();
    }

    public void setMatrix(int[][] newMatrix) {
        this.matrix = newMatrix;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        if (matrix == null || matrix.length == 0) return 0;
        return matrix.length * matrix[0].length;
    }

    @Override
    public Object getItem(int position) {
        int row = position / matrix[0].length;
        int col = position % matrix[0].length;
        return matrix[row][col];
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.grid_item, parent, false);
            holder = new ViewHolder((EditText) convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final int numCols = matrix[0].length;
        final int row = position / numCols;
        final int col = position % numCols;

        // Desenganchar watcher viejo antes de setText para evitar loops
        holder.editText.removeTextChangedListener(holder.textWatcher);
        holder.editText.setText(String.valueOf(matrix[row][col]));
        holder.textWatcher.updatePosition(row, col);
        holder.editText.addTextChangedListener(holder.textWatcher);

        boolean isTheSelectedCell = (row == selectedRow && col == selectedCol);

        if (isEditing && isTheSelectedCell) {
            holder.editText.setFocusable(true);
            holder.editText.setFocusableInTouchMode(true);
            holder.editText.setBackgroundColor(Color.YELLOW);
            holder.editText.requestFocus();
        } else {
            holder.editText.setFocusable(false);
            holder.editText.setFocusableInTouchMode(false);
            holder.editText.setBackgroundColor(isTheSelectedCell ? Color.CYAN : Color.LTGRAY);
        }

        holder.editText.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCellEdited(row, col, matrix[row][col]);
            }
        });

        return convertView;
    }

    private class ViewHolder {
        EditText editText;
        CustomTextWatcher textWatcher;

        ViewHolder(EditText editText) {
            this.editText = editText;
            this.textWatcher = new CustomTextWatcher();
        }
    }

    private class CustomTextWatcher implements TextWatcher {
        private int row;
        private int col;

        public void updatePosition(int row, int col) {
            this.row = row;
            this.col = col;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}

        @Override
        public void afterTextChanged(Editable s) {
            if (isEditing && row == selectedRow && col == selectedCol) {
                try {
                    int newValue = s.length() > 0 ? Integer.parseInt(s.toString()) : 0;

                    if (col == 0 && (newValue < 0 || newValue > 15)) {
                        Toast.makeText(context, "El valor debe estar entre 0 y 15", Toast.LENGTH_SHORT).show();
                        s.replace(0, s.length(), String.valueOf(matrix[row][col]));
                        return;
                    } else if (col > 0 && (newValue < 0 || newValue > 127)) {
                        Toast.makeText(context, "El valor debe estar entre 0 y 127", Toast.LENGTH_SHORT).show();
                        s.replace(0, s.length(), String.valueOf(matrix[row][col]));
                        return;
                    }

                    if (matrix[row][col] != newValue) {
                        matrix[row][col] = newValue;
                        if (listener != null) {
                            listener.onCellEdited(row, col, newValue);
                        }
                    }
                } catch (NumberFormatException e) {
                    Toast.makeText(context, "Por favor, ingrese solo números", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
}
