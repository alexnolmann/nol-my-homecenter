<?php
declare(strict_types=1);
namespace MediaCentre\Finance;
use MediaCentre\Storage\Database;
final class FinanceRepository
{
    public function __construct(private Database $db){}
    public function listForClient(string $client): array {return $this->db->all('SELECT i.*, w.sort_order,w.show_on_dashboard,q.price,q.nominal,q.previous_price,q.change_pct,q.price_kind,q.quote_as_of,q.data_as_of,q.effective_date,q.fetched_at,q.valid_until,q.delay_seconds,q.test_data,g.type source_type FROM client_finance_watchlist w JOIN finance_instruments i ON i.id=w.instrument_id JOIN integrations g ON g.id=i.integration_id LEFT JOIN finance_quotes q ON q.instrument_id=i.id AND q.instrument_revision=i.revision WHERE w.client_id=? ORDER BY w.sort_order',[$client]);}
}
