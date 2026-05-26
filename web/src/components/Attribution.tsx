interface AttributionProps {
  attribution: string;
  lastImportedAt: string | null;
}

export function Attribution({ attribution, lastImportedAt }: AttributionProps) {
  const formattedDate = lastImportedAt
    ? new Date(lastImportedAt).toLocaleString('pt-BR', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      })
    : null;

  return (
    <footer className="attribution" aria-label="Atribuição dos dados">
      <span className="attribution__text">{attribution}</span>
      {formattedDate && (
        <span className="attribution__date">
          {' '}· Atualizado em {formattedDate}
        </span>
      )}
    </footer>
  );
}
