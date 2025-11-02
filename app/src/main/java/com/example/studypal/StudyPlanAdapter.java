package com.example.studypal;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;

/**
 * RecyclerView adapter for StudyPlan items.
 * Exposes minimal responsibilities: display, handle checkbox and delete.
 */
public class StudyPlanAdapter extends RecyclerView.Adapter<StudyPlanAdapter.PlanViewHolder> {

    public interface OnActionListener {
        void onStatusChanged(String docId, boolean completed);
        void onDelete(String docId);
        void onItemClicked(StudyPlan plan);
    }

    private final Context context;
    private final ArrayList<StudyPlan> plans;
    private final OnActionListener listener;
    private final FirebaseFirestore db;

    public StudyPlanAdapter(Context context, ArrayList<StudyPlan> plans, OnActionListener listener) {
        this.context = context;
        this.plans = plans;
        this.listener = listener;
        this.db = FirebaseFirestore.getInstance();
    }

    @NonNull
    @Override
    public PlanViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_study_plan, parent, false);
        return new PlanViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull PlanViewHolder holder, int position) {
        StudyPlan plan = plans.get(position);
        holder.planTitle.setText(plan.getSubject());
        holder.planDescription.setText("Topics: " + plan.getTopics() + "\nDate: " + plan.getDate());

        // prevent reuse glitch
        holder.planCompleted.setOnCheckedChangeListener(null);
        holder.planCompleted.setChecked(plan.isCompleted());

        holder.planCompleted.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // update via listener -> activity will update Firestore (keeps separation)
            if (listener != null) listener.onStatusChanged(plan.getDocId(), isChecked);
        });

        holder.btnDeletePlan.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(plan.getDocId());
        });

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClicked(plan);
        });
    }

    @Override
    public int getItemCount() {
        return plans != null ? plans.size() : 0;
    }

    public void updateList(ArrayList<StudyPlan> newPlans) {
        plans.clear();
        plans.addAll(newPlans);
        notifyDataSetChanged();
    }

    public StudyPlan getAt(int pos) {
        return plans.get(pos);
    }

    public void removeByDocId(String docId) {
        for (int i = 0; i < plans.size(); i++) {
            if (docId != null && docId.equals(plans.get(i).getDocId())) {
                plans.remove(i);
                notifyItemRemoved(i);
                return;
            }
        }
    }

    static class PlanViewHolder extends RecyclerView.ViewHolder {
        final TextView planTitle, planDescription;
        final CheckBox planCompleted;
        final ImageButton btnDeletePlan;

        public PlanViewHolder(@NonNull View itemView) {
            super(itemView);
            planTitle = itemView.findViewById(R.id.planTitle);
            planDescription = itemView.findViewById(R.id.planDescription);
            planCompleted = itemView.findViewById(R.id.planCompleted);
            btnDeletePlan = itemView.findViewById(R.id.btnDeletePlan);
        }
    }
}
