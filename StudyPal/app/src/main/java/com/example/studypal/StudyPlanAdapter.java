package com.example.studypal;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class StudyPlanAdapter extends RecyclerView.Adapter<StudyPlanAdapter.PlanViewHolder> {

    private final List<StudyPlan> plans;
    private final OnPlanCompleteListener listener;

    public interface OnPlanCompleteListener {
        void onPlanCompleted(int planId, boolean isChecked);
    }

    public StudyPlanAdapter(List<StudyPlan> plans, OnPlanCompleteListener listener) {
        this.plans = plans;
        this.listener = listener;
    }

    @NonNull
    @Override
    public PlanViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_study_plan, parent, false);
        return new PlanViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull PlanViewHolder holder, int position) {
        StudyPlan plan = plans.get(position);

        holder.title.setText(plan.getSubject());
        holder.description.setText("Topics: " + plan.getTopics() + "\nDeadline: " + plan.getDate());

        holder.completed.setOnCheckedChangeListener(null);
        holder.completed.setChecked(plan.isCompleted());

        holder.completed.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (listener != null) {
                listener.onPlanCompleted(plan.getId(), isChecked);
            }
        });
    }

    @Override
    public int getItemCount() {
        return plans != null ? plans.size() : 0;
    }

    static class PlanViewHolder extends RecyclerView.ViewHolder {
        TextView title, description;
        CheckBox completed;

        public PlanViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.planTitle);
            description = itemView.findViewById(R.id.planDescription);
            completed = itemView.findViewById(R.id.planCompleted);
        }
    }
}
