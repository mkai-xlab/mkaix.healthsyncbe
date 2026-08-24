-- Adds the doctor-confirmed report result block (KET QUA / KET LUAN) to the examinations table,
-- so the next report draft can offer the doctor's own wording back instead of resetting to the
-- text auto-composed from the verified Kellgren-Lawrence grades.

ALTER TABLE examinations ADD COLUMN IF NOT EXISTS findings TEXT;
ALTER TABLE examinations ADD COLUMN IF NOT EXISTS conclusion TEXT;
