"""Anonymize historical public community contribution rows.

Revision ID: 0005_anonymize_contributions
Revises: 0004_community_contributions

Account identity is not required for public aggregate statistics. Existing
contribution totals are preserved while old account links are cleared.
"""
from alembic import op

revision = "0005_anonymize_contributions"
down_revision = "0004_community_contributions"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.execute("UPDATE community_contributions SET user_id = NULL WHERE user_id IS NOT NULL")


def downgrade() -> None:
    # Intentionally irreversible: account associations are privacy-redacted.
    pass
