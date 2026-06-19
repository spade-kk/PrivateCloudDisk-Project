package com.privateclouddisk.android.ui.main;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.privateclouddisk.android.R;
import com.privateclouddisk.android.data.model.NodeItem;
import com.privateclouddisk.android.util.FileUtils;

/**
 * 文件列表适配器
 * 使用 ListAdapter + DiffUtil 实现高效列表更新
 */
public class FileListAdapter extends ListAdapter<NodeItem, FileListAdapter.ViewHolder> {

    private final OnItemClickListener clickListener;
    private final OnItemLongClickListener longClickListener;

    public interface OnItemClickListener {
        void onClick(NodeItem item);
    }

    public interface OnItemLongClickListener {
        void onLongClick(NodeItem item);
    }

    public FileListAdapter(OnItemClickListener clickListener,
                            OnItemLongClickListener longClickListener) {
        super(DIFF_CALLBACK);
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
    }

    private static final DiffUtil.ItemCallback<NodeItem> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<NodeItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull NodeItem oldItem, @NonNull NodeItem newItem) {
                    return oldItem.getEffectiveId().equals(newItem.getEffectiveId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull NodeItem oldItem, @NonNull NodeItem newItem) {
                    return oldItem.getEffectiveName().equals(newItem.getEffectiveName())
                            && oldItem.getEffectiveSize() == newItem.getEffectiveSize()
                            && oldItem.getUpdatedTime() != null
                            && oldItem.getUpdatedTime().equals(newItem.getUpdatedTime());
                }
            };

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_file, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        NodeItem item = getItem(position);
        holder.bind(item, clickListener, longClickListener);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final ImageView ivIcon;
        private final TextView tvName;
        private final TextView tvInfo;
        private final TextView tvDate;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.iv_file_icon);
            tvName = itemView.findViewById(R.id.tv_file_name);
            tvInfo = itemView.findViewById(R.id.tv_file_info);
            tvDate = itemView.findViewById(R.id.tv_file_date);
        }

        void bind(NodeItem item, OnItemClickListener clickListener,
                   OnItemLongClickListener longClickListener) {
            tvName.setText(item.getEffectiveName());

            // 图标
            if (item.isDirectory()) {
                ivIcon.setImageResource(R.drawable.ic_folder);
            } else if (item.isImageFile()) {
                ivIcon.setImageResource(R.drawable.ic_image);
            } else if (item.isVideoFile()) {
                ivIcon.setImageResource(R.drawable.ic_video);
            } else if (item.isAudioFile()) {
                ivIcon.setImageResource(R.drawable.ic_audio);
            } else if (item.isDocumentFile()) {
                ivIcon.setImageResource(R.drawable.ic_document);
            } else {
                ivIcon.setImageResource(R.drawable.ic_file);
            }

            // 信息
            if (item.isDirectory()) {
                tvInfo.setText("文件夹");
            } else {
                tvInfo.setText(FileUtils.formatFileSize(item.getEffectiveSize()));
            }

            // 日期
            String date = item.getUpdatedTime();
            if (date == null) date = item.getUploadedTime();
            if (date == null) date = item.getCreatedTime();
            tvDate.setText(date != null ? FileUtils.formatDateTime(date) : "");

            // 点击事件
            itemView.setOnClickListener(v -> {
                if (clickListener != null) clickListener.onClick(item);
            });

            itemView.setOnLongClickListener(v -> {
                if (longClickListener != null) longClickListener.onLongClick(item);
                return true;
            });
        }
    }
}